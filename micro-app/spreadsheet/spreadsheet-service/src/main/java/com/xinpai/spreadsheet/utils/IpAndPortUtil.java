package com.xinpai.spreadsheet.utils;

import java.net.InetAddress;

import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class IpAndPortUtil implements EnvironmentAware {
    private static Environment env;

    /**
     * 获取ip地址及端口port
     * 
     * @return ip:port
     */
    public static String getIpAddressAndPort() {
        String host = "localhost";
        try {
            host = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
        }
        String port = env.getProperty("server.port");
        return host + ":" + port;
    }

    @Override
    public void setEnvironment(Environment environment) {
        env = environment;
    }
}
