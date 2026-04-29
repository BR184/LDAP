package com.company.idm.application.user;

import com.company.idm.common.exception.BizException;
import java.text.Normalizer;
import java.util.Locale;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.springframework.stereotype.Service;

/**
 * Generate stable usernames from real name and employee number.
 */
@Service
public class UsernameGenerationService {

    private static final int MAX_USERNAME_LENGTH = 64;
    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = new HanyuPinyinOutputFormat();

    static {
        PINYIN_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        PINYIN_FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    public String generate(String realName, String employeeNo) {
        String normalizedEmployeeNo = sanitizeEmployeeNo(employeeNo);
        if (normalizedEmployeeNo.isBlank()) {
            throw new BizException("USER_EMPLOYEE_NO_REQUIRED", "工号不能为空");
        }
        if (normalizedEmployeeNo.length() >= MAX_USERNAME_LENGTH) {
            throw new BizException("USER_EMPLOYEE_NO_INVALID", "工号长度超出限制");
        }

        String pinyin = sanitizeName(toPinyin(realName));
        if (pinyin.isBlank()) {
            pinyin = "user";
        }

        int maxPrefixLength = MAX_USERNAME_LENGTH - normalizedEmployeeNo.length();
        if (maxPrefixLength <= 0) {
            throw new BizException("USER_EMPLOYEE_NO_INVALID", "工号长度超出限制");
        }
        if (pinyin.length() > maxPrefixLength) {
            pinyin = pinyin.substring(0, maxPrefixLength);
        }
        return pinyin + normalizedEmployeeNo;
    }

    private String toPinyin(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char current : text.trim().toCharArray()) {
            if (Character.isLetterOrDigit(current) && current < 128) {
                builder.append(Character.toLowerCase(current));
                continue;
            }
            try {
                String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(current, PINYIN_FORMAT);
                if (pinyinArray != null && pinyinArray.length > 0) {
                    builder.append(pinyinArray[0].replaceAll("[^a-z0-9]", ""));
                }
            } catch (BadHanyuPinyinOutputFormatCombination exception) {
                throw new BizException("USER_USERNAME_GENERATE_FAILED", "用户名拼音生成失败");
            }
        }
        return builder.toString();
    }

    private String sanitizeName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
    }

    private String sanitizeEmployeeNo(String employeeNo) {
        if (employeeNo == null || employeeNo.isBlank()) {
            return "";
        }
        return employeeNo.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
