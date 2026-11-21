package com.xinpai.spreadsheet.websocket;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.alibaba.fastjson.JSONObject;
import com.xinpai.spreadsheet.db.ISpreadsheetDAO;
import com.xinpai.spreadsheet.redis.RedisLock;
import com.xinpai.spreadsheet.redis.RedisMessageModel;
import com.xinpai.spreadsheet.redis.RedisMessagePublish;
import com.xinpai.spreadsheet.service.TokenService;
import com.xinpai.spreadsheet.service.WriteSpreadsheetService;
import com.xinpai.spreadsheet.utils.GzipUtil;
import com.xinpai.spreadsheet.utils.JsonUtil;
import com.xinpai.spreadsheet.utils.StringUtil;
import com.xinpai.spreadsheet.utils.UrlUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Socket处理器(包括发送信息，接收信息，信息错误等方法。)
 *
 * 代码中rv，rv_end说明
 * 因为websocket传输大小限制
 * 批量更新范围单元格的时候
 * 一次最多1000个单元格
 * 要求 每次传'rv' 最后一次传他'rv_end'
 * rv_end就是个信号
 * 表示这次范围更新数据全部传输完,它自身这次不带数据过去的
 * 
 * @author Administrator
 */
@Component
public class SpreadsheetWebSocketHandler extends TextWebSocketHandler {
    final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private RedisMessagePublish redisMessagePublish;

    @Autowired
    private WriteSpreadsheetService writeSpreadsheetService;

    @Autowired
    @SuppressWarnings("rawtypes")
    private RedisTemplate redisTemplate;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ISpreadsheetDAO spreadsheetDAO;

    /**
     * 先注册一个websocket服务器，将连接上的所有用户放进去
     * 外层key spreadsheetId（文档id），内层key session ID（用户id）
     */
    private static final Hashtable<String, Hashtable<String, WSUserModel>> SPREADSHEET_SESSION_MAP;
    /**
     * 静态变量，用来记录当前在线连接数。应该把它设计成线程安全的。
     */
    private static int onlineCount = 0;

    public static String ipAndPort;

    static {
        SPREADSHEET_SESSION_MAP = new Hashtable<String, Hashtable<String, WSUserModel>>(12);
    }

    /**
     * 前台连接并且注册了账户
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        session.setTextMessageSizeLimit(2048000);
        session.setBinaryMessageSizeLimit(2048000);
        openConn(session);
    }

    /**
     * 接受消息
     * 
     * @param session
     * @param message
     * @throws Exception
     */
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (message.getPayloadLength() == 0) {
            // 无消息直接返回
            return;
        }
        WSUserModel user = new WSUserModel(session);
        String content = message.getPayload().toString();

        // 心跳包，延长token有效期后跳过
        if ("rub".equals(content)) {
            if (user.getReadonly()) {
                tokenService.extendReadTokenExpireTime(user.getToken());
            } else {
                tokenService.extendEditTokenExpireTime(user.getToken());
            }
            return;
        }

        String contentReal = GzipUtil.unCompressToURI(content);
        JSONObject json = null;
        try {
            json = JSONObject.parseObject(contentReal);
        } catch (Exception ex) {
            log.error("json字符串转换错误str:" + JSONObject.toJSONString(contentReal));
            return;
        }

        // 返回消息 type
        // 0: 连接成功，
        // 1: 发送给发送信息用户，
        // 2: 发送信息给其他用户，
        // 3: 发送选区位置信息
        // 999: 用户连接断开
        Consumer<Boolean> sendFeedback = (Boolean success) -> {
            Map<String, Object> msgMap = new HashMap<String, Object>();
            msgMap.put("type", 1);
            msgMap.put("createTime", System.currentTimeMillis());
            msgMap.put("status", success ? "0" : "1");
            msgMap.put("returnMessage", success ? "success" : "error");

            sendMessageToUser(session, msgMap);
        };
        BiConsumer<Boolean, Integer> publishToOther = (Boolean success, Integer type) -> {
            Map<String, Object> msgMap = new HashMap<String, Object>();
            msgMap.put("type", type);
            msgMap.put("username", user.getUserName());
            msgMap.put("id", "" + user.getId());
            msgMap.put("status", success ? "0" : "1");
            msgMap.put("returnMessage", success ? "success" : "error");
            msgMap.put("createTime", System.currentTimeMillis());
            msgMap.put("data", contentReal);

            publishToOtherUsers(user, msgMap);
        };

        String spreadsheetId = UrlUtil.urlDecode(user.getSpreadsheetId());
        if (json == null || StringUtil.isNullOrEmpty(spreadsheetId)) {
            return;
        }
        String key = spreadsheetId + user.getWs().getId();
        String optionType = json.get("t").toString();

        // 当只读时，除了光标移动之外的操作都不能执行
        if (user.isReadonly() && !"mv".equals(optionType)) {
            return;
        }
        switch (optionType) {
            // 光标移动
            case "mv":
                // 非只读时记录光标位置，否则只广播光标移动信息
                if (!user.isReadonly())
                    writeSpreadsheetService.Operation_mv(spreadsheetId, json);
                publishToOther.accept(true, 3);
                sendFeedback.accept(true);
                return;
            // 批量操作
            case "rv":
                writeSpreadsheetService.getIndexRvForThread(key, json);
                publishToOther.accept(true, 2);
                sendFeedback.accept(true);
                break;
            // 批量操作结束
            case "rv_end":
                String sheetIndex = json.get("i").toString();
                writeSpreadsheetService.updateRvDbContent(spreadsheetId, json, key + sheetIndex);
                publishToOther.accept(true, 2);
                sendFeedback.accept(true);
                break;
            // 其他操作
            default:
                RedisLock redisLock = new RedisLock(redisTemplate, spreadsheetId);
                try {
                    if (redisLock.lock()) {
                        String err = writeSpreadsheetService.handleUpdate(spreadsheetId, json);
                        if (err.length() > 0) {
                            throw new Exception(err);
                        }
                        spreadsheetDAO.updateLastUpdated(spreadsheetId, user.getUserName());
                        publishToOther.accept(true, 2);
                        sendFeedback.accept(true);
                    }
                } catch (Exception e) {
                    sendFeedback.accept(false);
                } finally {
                    redisLock.unlock();
                }
        }
    }

    /**
     * 消息传输错误处理，如果出现错误直接断开连接
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        if (session.isOpen()) {
            session.close();
        }
    }

    /**
     * 关闭连接后
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        closeConn(session, false);
    }

    private void openConn(WebSocketSession session) {
        try {
            // 创建一个连接窗口，并加入的队列中
            WSUserModel user = new WSUserModel(session);
            setUserOnline(user);

            Map<String, Object> msgMap = new HashMap<String, Object>();
            msgMap.put("message", "连接成功");
            msgMap.put("status", "0");
            msgMap.put("type", 0);

            sendMessageToUser(session, msgMap);
        } catch (Exception e) {
            log.error("建立ws连接失败:" + e);
        }
    }

    private void closeConn(WebSocketSession session, boolean isError) {
        WSUserModel user = new WSUserModel(session);
        setUserOffline(user);

        // 广播下线消息
        Map<String, Object> msgMap = new HashMap<String, Object>();
        msgMap.put("message", "用户退出");
        msgMap.put("type", 999);
        msgMap.put("username", user.getUserName());
        msgMap.put("id", "" + user.getId());

        publishToOtherUsers(user, msgMap);

    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private static void setUserOnline(WSUserModel userModel) {
        String spreadsheetId = userModel.getSpreadsheetId();
        String sessionId = userModel.getId();
        if (SPREADSHEET_SESSION_MAP.containsKey(spreadsheetId)) {
            SPREADSHEET_SESSION_MAP.get(spreadsheetId).put(sessionId, userModel);
        } else {
            Hashtable<String, WSUserModel> sessionMap = new Hashtable<String, WSUserModel>();
            sessionMap.put(sessionId, userModel);
            SPREADSHEET_SESSION_MAP.put(spreadsheetId, sessionMap);
        }
        incOnlineCount();
    }

    private static void setUserOffline(WSUserModel userModel) {
        String spreadsheetId = userModel.getSpreadsheetId();
        String sessionId = userModel.getId();
        if (SPREADSHEET_SESSION_MAP.containsKey(spreadsheetId)) {
            Hashtable<String, WSUserModel> sessionTable = SPREADSHEET_SESSION_MAP.get(spreadsheetId);
            sessionTable.remove(sessionId);
            if (sessionTable.isEmpty()) {
                SPREADSHEET_SESSION_MAP.remove(spreadsheetId);
            }
        }
        subOnlineCount();
    }

    public void publishToOtherUsers(WSUserModel user, String message) {
        // 通过redis发布消息
        redisMessagePublish.publishMessage(new RedisMessageModel(ipAndPort, user.getSpreadsheetId(), message));
        // 发给本机其他用户
        sendMessageToOtherUsers(user, message);
    }

    public void publishToOtherUsers(WSUserModel user, Map<String, ?> message) {
        publishToOtherUsers(user, JsonUtil.toJson(message));
    }

    /**
     * 群发消息（本节点，其他窗口）
     *
     * @param user    消息提供者
     * @param content 内容
     */
    public void sendMessageToOtherUsers(WSUserModel user, String content) {
        if (SPREADSHEET_SESSION_MAP == null
                || user == null
                || StringUtil.isNullOrEmpty(content)
                || !SPREADSHEET_SESSION_MAP.containsKey(user.getSpreadsheetId())) {
            return;
        }
        // 必须是同一个文档
        Hashtable<String, WSUserModel> users = SPREADSHEET_SESSION_MAP.get(user.getSpreadsheetId());
        sendMessageToOtherUsers(users, content, user.getWs().getId());
    }

    public void sendMessageToOtherUsers(WSUserModel user, Map<String, ?> content) {
        if (content == null)
            return;
        sendMessageToOtherUsers(user, JsonUtil.toJson(content));
    }

    /**
     * 群发消息（redis消息订阅）
     **/
    public static void sendMessageToUserByRedis(RedisMessageModel model) {
        sendMessageToUserByRedis(model.getIpAndPort(), model.getSpreadsheetId(), model.getContent());
    }

    /**
     * 群发消息（redis消息订阅）
     *
     * @param ipAndPort     提供数据的节点
     * @param spreadsheetId 文档id
     * @param message       内容
     */
    private static void sendMessageToUserByRedis(String ipAndPort, String spreadsheetId, String message) {
        if (SPREADSHEET_SESSION_MAP != null && ipAndPort != null && spreadsheetId != null && message != null) {
            if (!ipAndPort.equals(SpreadsheetWebSocketHandler.ipAndPort)) {
                // 不是本机的
                if (SPREADSHEET_SESSION_MAP.containsKey(spreadsheetId)) {
                    // 必须是同一个文档
                    Hashtable<String, WSUserModel> _userMap = SPREADSHEET_SESSION_MAP.get(spreadsheetId);
                    sendMessageToOtherUsers(_userMap, message, null);
                }
            }
        }
    }

    /**
     * 给一组用户发消息
     *
     * @param _userMap  用户组
     * @param _content  内容
     * @param sessionId 数据提供的用户sessionid（redis订阅设定为null）
     */
    private static void sendMessageToOtherUsers(Hashtable<String, WSUserModel> _userMap, String _content,
            String sessionId) {
        if (_userMap == null || StringUtil.isNullOrEmpty(_content)) {
            return;
        }
        TextMessage message = new TextMessage(_content);
        _userMap.forEach((sessionIdKey, userModel) -> {
            if (null == sessionId || !sessionIdKey.equals(sessionId)) {
                // 给（非消息提供者）打开改文档的用户发消息
                sendMessageToUser(userModel.getWs(), message);
            }
        });
    }

    /**
     * 给单个用户发消息
     *
     * @param session
     * @param message
     */
    @SuppressWarnings("rawtypes")
    private static void sendMessageToUser(WebSocketSession session, WebSocketMessage message) {
        try {
            synchronized (session) {
                session.sendMessage(message);
            }
        } catch (Exception ex) {
        }
    }

    private static void sendMessageToUser(WebSocketSession session, String message) {
        sendMessageToUser(session, new TextMessage(message));
    }

    private static void sendMessageToUser(WebSocketSession session, Map<String, ?> message) {
        sendMessageToUser(session, JsonUtil.toJson(message));
    }

    // 全局的在线人数
    public static synchronized int getOnlineCount() {
        return onlineCount;
    }

    public static synchronized void incOnlineCount() {
        onlineCount++;
    }

    public static synchronized void subOnlineCount() {
        onlineCount--;
    }
}
