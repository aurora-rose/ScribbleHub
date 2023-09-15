package com.cn.bdth.net;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cn.bdth.exceptions.ExceptionMessages;
import com.cn.bdth.exceptions.ViolationsException;
import com.cn.bdth.exceptions.WechatException;
import com.cn.bdth.service.ChatService;
import com.cn.bdth.service.impl.ChatServiceImpl;
import com.cn.bdth.utils.SpringContextUtil;
import com.cn.bdth.utils.UserUtils;
import com.cn.bdth.utils.WeChatUtils;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;


/**
 * 长连接响应.
 *
 * @author bdth
 */
@Slf4j
@ServerEndpoint("/bing/api/{token}")
@SuppressWarnings("all")
@Service
public class MiniBingWss {

    private Session session;
    private static ConcurrentHashMap<String, Session> webSocketSet = new ConcurrentHashMap<>();
    private static WeChatUtils weChatUtils;
    private static ChatService chatService;

    @OnOpen
    public void onOpen(Session session, @PathParam("token") String token) {
        this.session = session;
        webSocketSet.put(session.getId(), session);
        if (StpUtil.getLoginIdByToken(token) == null) {
            //关闭连接
            handleWebSocketCompletion();
        }
        if (chatService == null) {
            weChatUtils = (WeChatUtils) SpringContextUtil.getBean("weChatUtils");
            chatService = (ChatServiceImpl) SpringContextUtil.getBean("chatServiceImpl");
        }

    }


    /**
     * 发送消息
     *
     * @param messages 请求体数据
     * @param type     执行类型
     * @param token    用户token
     */
    @OnMessage
    public void onMessage(String messages, @PathParam("token") String token) {
        try {
            // 微信文字识别能力 防止用户发送色情 政治信息
            weChatUtils.filterText(messages, UserUtils.getOpenIdByToken(token));
            chatService.bingFlux(messages)
                    .timeout(Duration.ofSeconds(60))
                    .doOnError(TimeoutException.class, e -> handleWebSocketError(ExceptionMessages.GPT_TIMEOUT))
                    .doFinally(signal -> handleWebSocketCompletion())
                    .subscribe(data -> {
                        final JSONObject jsonObject = JSONObject.parseObject(data.toString());
                        for (Object arguments : jsonObject.getJSONArray("arguments")) {
                            final JSONObject json = JSONObject.parseObject(arguments.toString());
                            //校验数据格式 取内容
                            if (json.containsKey("messages") && (!json.containsKey("cursor"))) {
                                final JSONArray jsonArray = json.getJSONArray("messages").getJSONObject(0).getJSONArray("adaptiveCards");
                                final JSONObject body = jsonArray.getJSONObject(0).getJSONArray("body").getJSONObject(0);
                                //是否为回复
                                if (body.containsKey("text")) {
                                    //回复数据
                                    AppointSending(session.getId(), body.getString("text"));
                                }
                            }
                        }
                    }, throwable -> {
                        log.error("调用GPT时出现异常 异常信息:{} 异常类:{}", throwable.getMessage(), throwable.getClass());
                        handleWebSocketError(ExceptionMessages.GPT_TIMEOUT);
                    });

        } catch (WechatException | ViolationsException e) {
            AppointSending(session.getId(), e.getMessage());
            handleWebSocketCompletion();
            return;
        }

    }

    /**
     * 发送错误消息
     *
     * @param errMsg the err msg
     */
    private void handleWebSocketError(final String errMsg) {
        AppointSending(session.getId(), errMsg);
    }

    /**
     * 回收资源
     */
    @OnClose
    public void handleWebSocketCompletion() {
        webSocketSet.remove(this.session.getId());
        try {
            this.session.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 发送消息
     *
     * @param id      the id
     * @param message the message
     */
    public void AppointSending(final String id, final String message) {
        try {
            final Session conversation = webSocketSet.get(id);
            conversation.getBasicRemote().sendText(message);
        } catch (Exception e) {

        }
    }

}
