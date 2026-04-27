package com.company.idm.application.sync.feishu;

import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.config.FeishuFileImportProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;

/**
 * 负责从受控目录中解析飞书标准化导入文件。
 */
@Component
public class FeishuImportDocumentResolver {

    private static final TypeReference<List<FeishuDepartmentPayload>> DEPARTMENT_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<FeishuUserPayload>> USER_LIST_TYPE = new TypeReference<>() {
    };

    private static final String HEADER_NAME = "姓名";
    private static final String HEADER_MOBILE = "手机号码";
    private static final String HEADER_EMPLOYEE_NO = "工号";
    private static final String HEADER_STATUS = "人员状态";
    private static final String HEADER_DEPARTMENT = "部门";
    private static final String HEADER_DEPARTMENT_FULL_PATH = "部门(全路径)";
    private static final String HEADER_LEVEL_1 = "一级部门";
    private static final String HEADER_LEVEL_2 = "二级部门";
    private static final String HEADER_LEVEL_3 = "三级部门";
    private static final String HEADER_LEVEL_4 = "四级部门";
    private static final String HEADER_LEVEL_5 = "五级部门";
    private static final String HEADER_USER_ID = "用户id";
    private static final String HEADER_WORK_EMAIL = "工作邮箱";
    private static final String HEADER_PERSONAL_EMAIL = "个人邮箱";

    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = new HanyuPinyinOutputFormat();

    static {
        PINYIN_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        PINYIN_FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    private final FeishuFileImportProperties properties;
    private final ObjectMapper objectMapper;

    public FeishuImportDocumentResolver(FeishuFileImportProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<FeishuDepartmentPayload> resolveDepartments(String documentPath) {
        Path resolvedPath = resolveDocumentPath(documentPath);
        if (isWorkbookDocument(resolvedPath)) {
            return resolveFullImportDocument(documentPath).departments();
        }
        JsonNode rootNode = readJsonNode(resolvedPath);
        if (rootNode.isArray()) {
            return objectMapper.convertValue(rootNode, DEPARTMENT_LIST_TYPE);
        }
        JsonNode departmentsNode = rootNode.get("departments");
        if (departmentsNode != null && departmentsNode.isArray()) {
            return objectMapper.convertValue(departmentsNode, DEPARTMENT_LIST_TYPE);
        }
        throw new BizException("FEISHU_FILE_IMPORT_FORMAT_INVALID", "飞书导入文件格式不正确");
    }

    public List<FeishuUserPayload> resolveUsers(String documentPath) {
        Path resolvedPath = resolveDocumentPath(documentPath);
        if (isWorkbookDocument(resolvedPath)) {
            return resolveFullImportDocument(documentPath).users();
        }
        JsonNode rootNode = readJsonNode(resolvedPath);
        if (rootNode.isArray()) {
            return objectMapper.convertValue(rootNode, USER_LIST_TYPE);
        }
        JsonNode usersNode = rootNode.get("users");
        if (usersNode != null && usersNode.isArray()) {
            return objectMapper.convertValue(usersNode, USER_LIST_TYPE);
        }
        throw new BizException("FEISHU_FILE_IMPORT_FORMAT_INVALID", "飞书导入文件格式不正确");
    }

    public FeishuFullImportDocument resolveFullImportDocument(String documentPath) {
        Path resolvedPath = resolveDocumentPath(documentPath);
        return isWorkbookDocument(resolvedPath)
            ? readRosterWorkbookDocument(resolvedPath)
            : readJsonBundleDocument(resolvedPath);
    }

    private FeishuFullImportDocument readJsonBundleDocument(Path resolvedPath) {
        JsonNode rootNode = readJsonNode(resolvedPath);
        JsonNode departmentsNode = rootNode.get("departments");
        JsonNode usersNode = rootNode.get("users");
        if (departmentsNode == null || !departmentsNode.isArray() || usersNode == null || !usersNode.isArray()) {
            throw new BizException("FEISHU_FILE_IMPORT_FORMAT_INVALID", "飞书一键导入 JSON 文件必须包含 departments 与 users 数组");
        }
        return new FeishuFullImportDocument(
            objectMapper.convertValue(departmentsNode, DEPARTMENT_LIST_TYPE),
            objectMapper.convertValue(usersNode, USER_LIST_TYPE)
        );
    }

    private FeishuFullImportDocument readRosterWorkbookDocument(Path resolvedPath) {
        try (InputStream inputStream = Files.newInputStream(resolvedPath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                Map<String, Integer> headerIndex = resolveHeaderIndex(sheet);
                if (isRosterSheet(headerIndex)) {
                    return resolveRosterDocument(sheet, headerIndex);
                }
            }
        } catch (IOException exception) {
            throw new BizException("FEISHU_FILE_IMPORT_READ_FAILED", "飞书导入文件读取失败");
        }
        throw new BizException("FEISHU_FILE_IMPORT_FORMAT_INVALID", "飞书花名册工作簿缺少可识别的在职人员工作表");
    }

    private FeishuFullImportDocument resolveRosterDocument(Sheet sheet, Map<String, Integer> headerIndex) {
        DataFormatter formatter = new DataFormatter();
        Map<String, FeishuDepartmentPayload> departmentMap = new LinkedHashMap<>();
        List<RosterUserRow> rosterUsers = new ArrayList<>();
        Set<String> usedUsernames = new LinkedHashSet<>();

        int userOrder = 1;
        for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isRowBlank(row, formatter)) {
                continue;
            }

            String externalId = readRequiredCell(row, headerIndex, formatter, HEADER_USER_ID);
            String realName = readRequiredCell(row, headerIndex, formatter, HEADER_NAME);
            List<String> departmentHierarchy = resolveDepartmentHierarchy(row, headerIndex, formatter);
            String mainDepartmentExternalId = upsertDepartments(departmentHierarchy, departmentMap);
            String email = firstNonBlank(
                readOptionalCell(row, headerIndex, formatter, HEADER_WORK_EMAIL),
                readOptionalCell(row, headerIndex, formatter, HEADER_PERSONAL_EMAIL)
            );
            String username = generateUsername(realName, email, externalId, usedUsernames);

            rosterUsers.add(new RosterUserRow(
                externalId,
                username,
                realName,
                normalizeEmail(email),
                normalizeMobile(readOptionalCell(row, headerIndex, formatter, HEADER_MOBILE)),
                blankToNull(readOptionalCell(row, headerIndex, formatter, HEADER_EMPLOYEE_NO)),
                mainDepartmentExternalId,
                resolveUserStatus(readOptionalCell(row, headerIndex, formatter, HEADER_STATUS)),
                userOrder++
            ));
        }

        List<FeishuUserPayload> users = rosterUsers.stream()
            .map(item -> new FeishuUserPayload(
                item.externalId(),
                item.username(),
                item.realName(),
                item.email(),
                item.mobile(),
                item.employeeNo(),
                item.mainDepartmentExternalId(),
                item.status(),
                item.orderNo()
            ))
            .toList();

        return new FeishuFullImportDocument(new ArrayList<>(departmentMap.values()), users);
    }

    private boolean isRosterSheet(Map<String, Integer> headerIndex) {
        return headerIndex.containsKey(normalizeHeader(HEADER_NAME))
            && headerIndex.containsKey(normalizeHeader(HEADER_USER_ID))
            && (headerIndex.containsKey(normalizeHeader(HEADER_DEPARTMENT_FULL_PATH))
            || headerIndex.containsKey(normalizeHeader(HEADER_LEVEL_1))
            || headerIndex.containsKey(normalizeHeader(HEADER_DEPARTMENT)));
    }

    private String upsertDepartments(List<String> hierarchy, Map<String, FeishuDepartmentPayload> departmentMap) {
        String parentExternalId = null;
        String prefix = "";
        for (String name : hierarchy) {
            prefix = prefix.isBlank() ? name : prefix + "/" + name;
            if (!departmentMap.containsKey(prefix)) {
                departmentMap.put(prefix, new FeishuDepartmentPayload(
                    buildDepartmentExternalId(prefix),
                    buildDepartmentCode(prefix),
                    name,
                    parentExternalId,
                    1,
                    departmentMap.size() + 1
                ));
            }
            parentExternalId = departmentMap.get(prefix).externalId();
        }
        return parentExternalId;
    }

    private List<String> resolveDepartmentHierarchy(Row row, Map<String, Integer> headerIndex, DataFormatter formatter) {
        List<String> hierarchy = new ArrayList<>();
        addIfPresent(hierarchy, readOptionalCell(row, headerIndex, formatter, HEADER_LEVEL_1));
        addIfPresent(hierarchy, readOptionalCell(row, headerIndex, formatter, HEADER_LEVEL_2));
        addIfPresent(hierarchy, readOptionalCell(row, headerIndex, formatter, HEADER_LEVEL_3));
        addIfPresent(hierarchy, readOptionalCell(row, headerIndex, formatter, HEADER_LEVEL_4));
        addIfPresent(hierarchy, readOptionalCell(row, headerIndex, formatter, HEADER_LEVEL_5));
        if (!hierarchy.isEmpty()) {
            return hierarchy;
        }

        String fullPath = readOptionalCell(row, headerIndex, formatter, HEADER_DEPARTMENT_FULL_PATH);
        if (fullPath != null && !fullPath.isBlank()) {
            List<String> segments = java.util.Arrays.stream(fullPath.split("[/\\\\]"))
                .map(String::trim)
                .filter(segment -> !segment.isBlank())
                .toList();
            List<String> filtered = new ArrayList<>(segments);
            while (filtered.size() > 1 && isSyntheticRootSegment(filtered.get(0))) {
                filtered.remove(0);
            }
            if (!filtered.isEmpty()) {
                return filtered;
            }
        }

        String departmentName = readOptionalCell(row, headerIndex, formatter, HEADER_DEPARTMENT);
        if (departmentName != null && !departmentName.isBlank()) {
            return List.of(departmentName);
        }

        throw new BizException("FEISHU_FILE_IMPORT_FORMAT_INVALID", "飞书花名册中存在缺少部门信息的用户行");
    }

    private boolean isSyntheticRootSegment(String segment) {
        return segment.contains("组织") || segment.contains("公司") || segment.contains("集团");
    }

    private String generateUsername(String realName, String email, String externalId, Set<String> usedUsernames) {
        String base = extractEmailLocalPart(email);
        if (base == null || base.isBlank()) {
            base = toPinyin(realName);
        }
        if (base == null || base.isBlank()) {
            base = "user";
        }
        base = sanitizeUsername(base);
        if (base.isBlank()) {
            base = "user";
        }

        String candidate = base;
        if (usedUsernames.add(candidate)) {
            return candidate;
        }

        String suffix = sanitizeUsername(externalId);
        if (suffix.length() > 6) {
            suffix = suffix.substring(0, 6);
        }
        candidate = base + "_" + suffix;
        if (usedUsernames.add(candidate)) {
            return candidate;
        }

        int index = 2;
        while (!usedUsernames.add(candidate + index)) {
            index++;
        }
        return candidate + index;
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
                throw new BizException("FEISHU_FILE_IMPORT_PINYIN_FAILED", "飞书花名册用户名拼音转换失败");
            }
        }
        return sanitizeUsername(builder.toString());
    }

    private String sanitizeUsername(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
    }

    private String extractEmailLocalPart(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return null;
        }
        String localPart = email.substring(0, email.indexOf('@'));
        return sanitizeUsername(localPart);
    }

    private String normalizeMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return null;
        }
        String digits = mobile.replaceAll("\\D", "");
        if (digits.length() == 13 && digits.startsWith("86")) {
            digits = digits.substring(2);
        }
        return digits.isBlank() ? null : digits;
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private Integer resolveUserStatus(String statusText) {
        if (statusText == null || statusText.isBlank()) {
            return 1;
        }
        String normalized = statusText.trim();
        return normalized.contains("在职") ? 1 : 0;
    }

    private String buildDepartmentExternalId(String pathKey) {
        return "roster_dept_" + sha1(pathKey).substring(0, 24);
    }

    private String buildDepartmentCode(String pathKey) {
        return "FD_" + sha1(pathKey).substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String sha1(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new BizException("FEISHU_FILE_IMPORT_HASH_FAILED", "飞书花名册导入哈希计算失败");
        }
    }

    private Map<String, Integer> resolveHeaderIndex(Sheet sheet) {
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        Map<String, Integer> headerIndex = new LinkedHashMap<>();
        if (headerRow == null) {
            return headerIndex;
        }
        DataFormatter formatter = new DataFormatter();
        short lastCellNum = headerRow.getLastCellNum();
        for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
            String header = formatter.formatCellValue(headerRow.getCell(cellIndex));
            if (header != null && !header.isBlank()) {
                headerIndex.put(normalizeHeader(header), cellIndex);
            }
        }
        return headerIndex;
    }

    private boolean isRowBlank(Row row, DataFormatter formatter) {
        short firstCellNum = row.getFirstCellNum();
        short lastCellNum = row.getLastCellNum();
        for (int cellIndex = Math.max(firstCellNum, 0); cellIndex < lastCellNum; cellIndex++) {
            if (!formatter.formatCellValue(row.getCell(cellIndex)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String readRequiredCell(Row row, Map<String, Integer> headerIndex, DataFormatter formatter, String headerKey) {
        String value = readOptionalCell(row, headerIndex, formatter, headerKey);
        if (value == null || value.isBlank()) {
            throw new BizException("FEISHU_FILE_IMPORT_FORMAT_INVALID", "飞书花名册中存在必填列空值");
        }
        return value;
    }

    private String readOptionalCell(Row row, Map<String, Integer> headerIndex, DataFormatter formatter, String headerKey) {
        Integer index = headerIndex.get(normalizeHeader(headerKey));
        if (index == null) {
            return null;
        }
        if (row.getCell(index) == null) {
            return null;
        }
        String value = formatter.formatCellValue(row.getCell(index));
        return value == null || value.isBlank() ? null : value.trim();
    }

    private JsonNode readJsonNode(Path resolvedPath) {
        try {
            validateFileSize(resolvedPath);
            return objectMapper.readTree(Files.readString(resolvedPath));
        } catch (IOException exception) {
            throw new BizException("FEISHU_FILE_IMPORT_READ_FAILED", "飞书导入文件读取失败");
        }
    }

    private void validateFileSize(Path resolvedPath) throws IOException {
        if (Files.size(resolvedPath) > properties.getMaxFileSizeBytes()) {
            throw new BizException("FEISHU_FILE_IMPORT_TOO_LARGE", "飞书导入文件大小超出限制");
        }
    }

    private boolean isWorkbookDocument(Path resolvedPath) {
        return resolvedPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx");
    }

    private Path resolveDocumentPath(String documentPath) {
        if (!properties.isEnabled()) {
            throw new BizException("FEISHU_FILE_IMPORT_DISABLED", "飞书文件导入未启用");
        }
        if (documentPath == null || documentPath.isBlank()) {
            throw new BizException("FEISHU_FILE_IMPORT_PATH_INVALID", "飞书导入文件路径不能为空");
        }
        String sanitizedDocumentPath = sanitizeDocumentPath(documentPath);
        Path rootPath = Paths.get(properties.getRootDir()).toAbsolutePath().normalize();
        Path candidatePath = Paths.get(sanitizedDocumentPath);
        Path resolvedPath = candidatePath.isAbsolute()
            ? candidatePath.toAbsolutePath().normalize()
            : rootPath.resolve(candidatePath).normalize();
        if (!resolvedPath.startsWith(rootPath)) {
            throw new BizException("FEISHU_FILE_IMPORT_PATH_INVALID", "飞书导入文件路径不在允许目录内");
        }
        String lowerCaseName = resolvedPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!lowerCaseName.endsWith(".json") && !lowerCaseName.endsWith(".xlsx")) {
            throw new BizException("FEISHU_FILE_IMPORT_FORMAT_INVALID", "飞书导入文件仅支持 JSON 或 XLSX 格式");
        }
        if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
            throw new BizException("FEISHU_FILE_IMPORT_NOT_FOUND", "飞书导入文件不存在");
        }
        return resolvedPath;
    }

    private String sanitizeDocumentPath(String documentPath) {
        String sanitized = documentPath == null ? "" : documentPath.trim();
        while (isWrappedByQuotes(sanitized)) {
            sanitized = sanitized.substring(1, sanitized.length() - 1).trim();
        }
        return sanitized;
    }

    private boolean isWrappedByQuotes(String value) {
        if (value == null || value.length() < 2) {
            return false;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        return (first == '"' && last == '"')
            || (first == '\'' && last == '\'')
            || (first == '“' && last == '”')
            || (first == '‘' && last == '’');
    }

    private String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        return header.trim()
            .replace("_", "")
            .replace("-", "")
            .replace(" ", "")
            .replace("（", "(")
            .replace("）", ")")
            .toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private void addIfPresent(List<String> bucket, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim();
        if (bucket.isEmpty() || !bucket.get(bucket.size() - 1).equals(normalized)) {
            bucket.add(normalized);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record RosterUserRow(
        String externalId,
        String username,
        String realName,
        String email,
        String mobile,
        String employeeNo,
        String mainDepartmentExternalId,
        Integer status,
        Integer orderNo
    ) {
    }
}
