package com.laison.erp.common.utils;

import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Desc
 * @Author sdp
 * @Date 2021/4/1 18:46
 */

public class MybatisExtendedLanguageDriver extends XMLLanguageDriver
        implements LanguageDriver {
    private final Pattern inPattern = Pattern.compile("\\(#\\{([\\.|\\w]+)\\}\\)");
    public SqlSource createSqlSource(Configuration configuration, String script, Class<?> parameterType) {
        Matcher matcher = inPattern.matcher(script);
        if (matcher.find()) {
            script = matcher.replaceAll("(<foreach collection=\"$1\" item=\"__item\" separator=\",\" >#{__item}</foreach>)");
        }
        script = "<script>" + script + "</script>";
        return super.createSqlSource(configuration, script, parameterType);
    }
}
