package com.cn.bdth.utils;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.cn.bdth.constants.auth.AuthConstant;

/**
 * 用户工具类
 *
 * @author 时间海 @github dulaiduwang003
 * @version 1.0
 */
public class UserUtils {


    public static Long getLoginIdByToken(final String token) {
        final Object loginIdObject = StpUtil.getLoginIdByToken(token);
        return Long.parseLong(String.valueOf(loginIdObject));
    }

    public static Long getCurrentLoginId() {
        return Long.parseLong(String.valueOf(StpUtil.getLoginId()));
    }

    public static Long isLoginReturnId() {
        if (StpUtil.isLogin()) {
            return getCurrentLoginId();
        } else {
            return null;
        }
    }


    public static String getCurrentRole() {
        final SaSession session = StpUtil.getSession();
        return String.valueOf(session.get(AuthConstant.ROLE));
    }


    public static String getCurrentOpenId() {
        final SaSession session = StpUtil.getSession();
        return String.valueOf(session.get(AuthConstant.OPEN_ID));
    }

    public static String getOpenIdByToken(final String token) {
        final SaSession session = StpUtil.getSessionByLoginId(getLoginIdByToken(token));

        return String.valueOf(session.get(AuthConstant.OPEN_ID));
    }
}
