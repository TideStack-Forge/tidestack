package com.ouroboros.notification.jpush.adapter;

import com.ouroboros.notification.adapter.NotificationChannelOptions;

import cn.jpush.api.push.model.Options;

/**
 * @author liansz
 */
public class JPushNotificationChannelOptions implements NotificationChannelOptions {

  /**
   * 推送可选参数
   */
  private Options options = Options.newBuilder().setApnsProduction(true).build();

  /**
   * 通知选项
   */
  private NotificationOptions notification;

  public static class NotificationOptions {

    /**
     * android平台
     */
    private Android android = new Android();

    /**
     * ios平台
     */
    private Ios ios = new Ios();

    /**
     * 字段详情参考
     * https://docs.jiguang.cn/jpush/server/push/rest_api_v3_push
     */
    public static class Android {
      private String title;
      private Integer builderId;
      private String channelId;
      private Integer priority;
      private String category;
      private Integer style = 1;
      private Integer alert_type;
      private String big_text;
      private Object inbox;
      private String big_pic_path;
      private String large_icon;
      private String small_icon_uri;
      private String uri_activity;
      private String uri_action;
      private Integer badge_add_num;
      private String badge_class;
      private String sound;
      private String show_begin_time;
      private String show_end_time;
      private String display_foreground;

      public String getTitle() {
        return title;
      }

      public void setTitle(String title) {
        this.title = title;
      }

      public Integer getBuilderId() {
        return builderId;
      }

      public void setBuilderId(Integer builderId) {
        this.builderId = builderId;
      }

      public String getChannelId() {
        return channelId;
      }

      public void setChannelId(String channelId) {
        this.channelId = channelId;
      }

      public Integer getPriority() {
        return priority;
      }

      public void setPriority(Integer priority) {
        this.priority = priority;
      }

      public String getCategory() {
        return category;
      }

      public void setCategory(String category) {
        this.category = category;
      }

      public Integer getStyle() {
        return style;
      }

      public void setStyle(Integer style) {
        this.style = style;
      }

      public Integer getAlert_type() {
        return alert_type;
      }

      public void setAlert_type(Integer alert_type) {
        this.alert_type = alert_type;
      }

      public String getBig_text() {
        return big_text;
      }

      public void setBig_text(String big_text) {
        this.big_text = big_text;
      }

      public Object getInbox() {
        return inbox;
      }

      public void setInbox(Object inbox) {
        this.inbox = inbox;
      }

      public String getBig_pic_path() {
        return big_pic_path;
      }

      public void setBig_pic_path(String big_pic_path) {
        this.big_pic_path = big_pic_path;
      }

      public String getLarge_icon() {
        return large_icon;
      }

      public void setLarge_icon(String large_icon) {
        this.large_icon = large_icon;
      }

      public String getSmall_icon_uri() {
        return small_icon_uri;
      }

      public void setSmall_icon_uri(String small_icon_uri) {
        this.small_icon_uri = small_icon_uri;
      }

      public String getUri_activity() {
        return uri_activity;
      }

      public void setUri_activity(String uri_activity) {
        this.uri_activity = uri_activity;
      }

      public String getUri_action() {
        return uri_action;
      }

      public void setUri_action(String uri_action) {
        this.uri_action = uri_action;
      }

      public Integer getBadge_add_num() {
        return badge_add_num;
      }

      public void setBadge_add_num(Integer badge_add_num) {
        this.badge_add_num = badge_add_num;
      }

      public String getBadge_class() {
        return badge_class;
      }

      public void setBadge_class(String badge_class) {
        this.badge_class = badge_class;
      }

      public String getSound() {
        return sound;
      }

      public void setSound(String sound) {
        this.sound = sound;
      }

      public String getShow_begin_time() {
        return show_begin_time;
      }

      public void setShow_begin_time(String show_begin_time) {
        this.show_begin_time = show_begin_time;
      }

      public String getShow_end_time() {
        return show_end_time;
      }

      public void setShow_end_time(String show_end_time) {
        this.show_end_time = show_end_time;
      }

      public String getDisplay_foreground() {
        return display_foreground;
      }

      public void setDisplay_foreground(String display_foreground) {
        this.display_foreground = display_foreground;
      }
    }

    /**
     * 字段详情参考
     * https://docs.jiguang.cn/jpush/server/push/rest_api_v3_push
     */
    public static class Ios {
      private Object sound;
      private String badge;
      private Boolean contentAvailable;
      private Boolean soundDisabled;
      private Boolean badgeDisabled;
      private String category;
      private Boolean mutableContent;
      private String threadId;

      public Object getSound() {
        return sound;
      }

      public void setSound(Object sound) {
        this.sound = sound;
      }

      public String getBadge() {
        return badge;
      }

      public void setBadge(String badge) {
        this.badge = badge;
      }

      public Boolean getContentAvailable() {
        return contentAvailable;
      }

      public void setContentAvailable(Boolean contentAvailable) {
        this.contentAvailable = contentAvailable;
      }

      public Boolean getSoundDisabled() {
        return soundDisabled;
      }

      public void setSoundDisabled(Boolean soundDisabled) {
        this.soundDisabled = soundDisabled;
      }

      public Boolean getBadgeDisabled() {
        return badgeDisabled;
      }

      public void setBadgeDisabled(Boolean badgeDisabled) {
        this.badgeDisabled = badgeDisabled;
      }

      public String getCategory() {
        return category;
      }

      public void setCategory(String category) {
        this.category = category;
      }

      public Boolean getMutableContent() {
        return mutableContent;
      }

      public void setMutableContent(Boolean mutableContent) {
        this.mutableContent = mutableContent;
      }

      public String getThreadId() {
        return threadId;
      }

      public void setThreadId(String threadId) {
        this.threadId = threadId;
      }
    }

    public Android getAndroid() {
      return android;
    }

    public void setAndroid(Android android) {
      this.android = android;
    }

    public Ios getIos() {
      return ios;
    }

    public void setIos(Ios ios) {
      this.ios = ios;
    }
  }

  public Options getOptions() {
    return options;
  }

  public void setOptions(Options options) {
    this.options = options;
  }

  public NotificationOptions getNotification() {
    return notification;
  }

  public void setNotification(NotificationOptions notification) {
    this.notification = notification;
  }
}
