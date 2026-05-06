package com.company.idm.application.sync.feishu;

import com.company.idm.application.user.UsernameGenerationService;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Component;

/**
 * Resolve FEISHU import documents from the controlled import directory.
 */
@Component
public class FeishuImportDocumentResolver {

    private static final int MAX_HEADER_SCAN_ROWS = 20;

    private static final TypeReference<List<FeishuDepartmentPayload>> DEPARTMENT_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<FeishuUserPayload>> USER_LIST_TYPE = new TypeReference<>() {
    };

    private static final String HEADER_NAME = "姓名";
    private static final String HEADER_MOBILE = "手机号";
    private static final String HEADER_MOBILE_NUMBER = "手机号码";
    private static final String HEADER_EMPLOYEE_NO = "工号";
    private static final String HEADER_STATUS = "人员状态";
    private static final String HEADER_DEPARTMENT = "部门";
    private static final String HEADER_DEPARTMENT_FULL_PATH = "部门(全路径)";
    private static final String HEADER_DEPARTMENT_FULL_PATH_WITH_SPACE = "部门 (全路径)";
    private static final String HEADER_LEVEL_1 = "一级部门";
    private static final String HEADER_LEVEL_2 = "二级部门";
    private static final String HEADER_LEVEL_3 = "三级部门";
    private static final String HEADER_LEVEL_4 = "四级部门";
    private static final String HEADER_LEVEL_5 = "五级部门";
    private static final String HEADER_USER_ID = "用户ID";
    private static final String HEADER_USER_ID_LOWER = "用户id";
    private static final String HEADER_WORK_EMAIL = "工作邮箱";

    private final FeishuFileImportProperties properties;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final UsernameGenerationService usernameGenerationService;

    public FeishuImportDocumentResolver(
        FeishuFileImportProperties properties,
        ObjectMapper objectMapper,
        UserRepository userRepository,
        UsernameGenerationService usernameGenerationService
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.usernameGenerationService = usernameGenerationService;
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
            throw new BizException("FEISHU_FILE_IMPORT_FORMAT_INVALID", "飞书一键导入 JSON 文件必须包含 departments 和 users 数组");
        }
        return new FeishuFullImportDocument(
            objectMapper.convertValue(departmentsNode, DEPARTMENT_LIST_TYPE),
            objectMapper.convertValue(usersNode, USER_LIST_TYPE)
        );
    }

    private FeishuFullImportDocument readRosterWorkbookDocument(Path resolvedPath) {
        try (InputStream inputStream = Files.newInputStream(resolvedPath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                Sheet sheet = workbook.getSheetAt(index);
                ResolvedHeader resolvedHeader = resolveHeader(sheet);
                if (resolvedHeader != null) {
                    return resolveRosterDocument(sheet, resolvedHeader);
                }
            }
        } catch (IOException exception) {
            throw new BizException("FEISHU_FILE_IMPORT_READ_FAILED", "飞书导入文件读取失败");
        }
        throw new BizException("FEISHU_FILE_IMPORT_FORMAT_INVALID", "飞书花名册工作簿缺少可识别的在职人员工作表");
    }

    private FeishuFullImportDocument resolveRosterDocument(Sheet sheet, ResolvedHeader resolvedHeader) {
        DataFormatter formatter = new DataFormatter();
        Map<String, FeishuDepartmentPayload> departmentMap = new LinkedHashMap<>();
        List<RosterUserRow> rosterUsers = new ArrayList<>();
        List<User> existingUsers = userRepository.findAll();
        Map<String, User> existingUserByExternalId = buildExistingUserByExternalId(existingUsers);
        Map<String, Integer> headerIndex = resolvedHeader.headerIndex();

        int orderNo = 1;
        for (int rowIndex = resolvedHeader.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isRowBlank(row, formatter)) {
                continue;
            }

            String externalId = readRequiredCell(row, headerIndex, formatter, HEADER_USER_ID, HEADER_USER_ID_LOWER);
            String realName = readRequiredCell(row, headerIndex, formatter, HEADER_NAME);
            String employeeNo = readRequiredCell(row, headerIndex, formatter, HEADER_EMPLOYEE_NO);
            List<List<String>> departmentHierarchies = resolveDepartmentHierarchies(row, headerIndex, formatter);
            if (departmentHierarchies.isEmpty()) {
                throw new BizException("FEISHU_FILE_IMPORT_FORMAT_INVALID", "飞书花名册中存在缺少部门信息的用户行");
            }

            List<String> departmentExternalIds = new ArrayList<>();
            for (List<String> hierarchy : departmentHierarchies) {
                departmentExternalIds.add(upsertDepartmentHierarchy(hierarchy, departmentMap));
            }

            String email = readOptionalCell(row, headerIndex, formatter, HEADER_WORK_EMAIL);
            User existingUser = existingUserByExternalId.get(externalId);
            String username = existingUser != null
                ? existingUser.getUsername()
                : usernameGenerationService.generate(realName, employeeNo);

            rosterUsers.add(new RosterUserRow(
                externalId,
                username,
                realName,
                normalizeEmail(email),
                normalizeMobile(readOptionalCell(row, headerIndex, formatter, HEADER_MOBILE, HEADER_MOBILE_NUMBER)),
                employeeNo,
                departmentExternalIds.get(0),
                departmentExternalIds.size() > 1 ? departmentExternalIds.subList(1, departmentExternalIds.size()) : List.of(),
                resolveUserStatus(readOptionalCell(row, headerIndex, formatter, HEADER_STATUS)),
                orderNo++
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
                item.partTimeDepartmentExternalIds(),
                item.status(),
                item.orderNo()
            ))
            .toList();

        return new FeishuFullImportDocument(new ArrayList<>(departmentMap.values()), users);
    }

    private boolean isRosterSheet(Map<String, Integer> headerIndex) {
        return headerIndex.containsKey(normalizeHeader(HEADER_NAME))
            && (headerIndex.containsKey(normalizeHeader(HEADER_USER_ID))
            || headerIndex.containsKey(normalizeHeader(HEADER_USER_ID_LOWER)))
            && headerIndex.containsKey(normalizeHeader(HEADER_EMPLOYEE_NO))
            && (headerIndex.containsKey(normalizeHeader(HEADER_DEPARTMENT_FULL_PATH))
            || headerIndex.containsKey(normalizeHeader(HEADER_DEPARTMENT_FULL_PATH_WITH_SPACE))
            || headerIndex.containsKey(normalizeHeader(HEADER_LEVEL_1))
            || headerIndex.containsKey(normalizeHeader(HEADER_DEPARTMENT)));
    }

    private String upsertDepartmentHierarchy(List<String> hierarchy, Map<String, FeishuDepartmentPayload> departmentMap) {
        String parentExternalId = null;
        String pathKey = "";
        for (String name : hierarchy) {
            pathKey = pathKey.isBlank() ? name : pathKey + "/" + name;
            if (!departmentMap.containsKey(pathKey)) {
                departmentMap.put(pathKey, new FeishuDepartmentPayload(
                    buildDepartmentExternalId(pathKey),
                    buildDepartmentCode(pathKey),
                    name,
                    parentExternalId,
                    1,
                    departmentMap.size() + 1
                ));
            }
            parentExternalId = departmentMap.get(pathKey).externalId();
        }
        return parentExternalId;
    }

    private List<List<String>> resolveDepartmentHierarchies(Row row, Map<String, Integer> headerIndex, DataFormatter formatter) {
        LinkedHashMap<String, List<String>> hierarchies = new LinkedHashMap<>();
        List<String> levelHierarchy = resolveLevelHierarchy(row, headerIndex, formatter);

        String explicitPaths = firstNonBlank(
            readOptionalCell(row, headerIndex, formatter, HEADER_DEPARTMENT_FULL_PATH),
            readOptionalCell(row, headerIndex, formatter, HEADER_DEPARTMENT_FULL_PATH_WITH_SPACE),
            readOptionalCell(row, headerIndex, formatter, HEADER_DEPARTMENT)
        );
        for (String rawPath : splitDepartmentPaths(explicitPaths)) {
            List<String> hierarchy = resolveHierarchyFromRawPath(rawPath);
            if (!hierarchy.isEmpty()) {
                hierarchies.putIfAbsent(String.join("/", hierarchy), hierarchy);
            }
        }

        if (!hierarchies.isEmpty() && !levelHierarchy.isEmpty()) {
            List<List<String>> existingHierarchies = new ArrayList<>(hierarchies.values());
            List<String> firstHierarchy = existingHierarchies.get(0);
            List<String> preferredHierarchy = new ArrayList<>(levelHierarchy);
            if (firstHierarchy.size() == levelHierarchy.size() + 1) {
                preferredHierarchy.add(0, firstHierarchy.get(0));
            }
            existingHierarchies.set(0, preferredHierarchy);
            hierarchies.clear();
            for (List<String> hierarchy : existingHierarchies) {
                hierarchies.putIfAbsent(String.join("/", hierarchy), hierarchy);
            }
        }

        if (hierarchies.isEmpty()) {
            if (!levelHierarchy.isEmpty()) {
                hierarchies.put(String.join("/", levelHierarchy), levelHierarchy);
            }
        }

        return new ArrayList<>(hierarchies.values());
    }

    private List<String> resolveLevelHierarchy(Row row, Map<String, Integer> headerIndex, DataFormatter formatter) {
        List<String> hierarchy = new ArrayList<>();
        addIfPresent(hierarchy, readOptionalCell(row, headerIndex, formatter, HEADER_LEVEL_1));
        addIfPresent(hierarchy, readOptionalCell(row, headerIndex, formatter, HEADER_LEVEL_2));
        addIfPresent(hierarchy, readOptionalCell(row, headerIndex, formatter, HEADER_LEVEL_3));
        addIfPresent(hierarchy, readOptionalCell(row, headerIndex, formatter, HEADER_LEVEL_4));
        addIfPresent(hierarchy, readOptionalCell(row, headerIndex, formatter, HEADER_LEVEL_5));
        return hierarchy;
    }

    private List<String> splitDepartmentPaths(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(rawValue.split("[,，;；\\r\\n]+"))
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .toList();
    }

    private List<String> resolveHierarchyFromRawPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return List.of();
        }
        String separatorPattern = rawPath.contains("/") || rawPath.contains("\\") ? "[/\\\\]" : "-";
        return java.util.Arrays.stream(rawPath.split(separatorPattern))
            .map(String::trim)
            .filter(segment -> !segment.isBlank())
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private Map<String, User> buildExistingUserByExternalId(List<User> existingUsers) {
        Map<String, User> result = new LinkedHashMap<>();
        for (User user : existingUsers) {
            if (user.getExternalId() == null || user.getExternalId().isBlank()) {
                continue;
            }
            result.putIfAbsent(user.getExternalId(), user);
        }
        return result;
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
        return statusText.trim().contains("在职") ? 1 : 0;
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

    private ResolvedHeader resolveHeader(Sheet sheet) {
        DataFormatter formatter = new DataFormatter();
        int firstRowNum = sheet.getFirstRowNum();
        int lastRowNum = Math.min(sheet.getLastRowNum(), firstRowNum + MAX_HEADER_SCAN_ROWS - 1);
        for (int rowIndex = firstRowNum; rowIndex <= lastRowNum; rowIndex++) {
            Row headerRow = sheet.getRow(rowIndex);
            if (headerRow == null || isRowBlank(headerRow, formatter)) {
                continue;
            }
            Map<String, Integer> headerIndex = new LinkedHashMap<>();
            short lastCellNum = headerRow.getLastCellNum();
            for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
                String header = formatter.formatCellValue(headerRow.getCell(cellIndex));
                if (header != null && !header.isBlank()) {
                    headerIndex.put(normalizeHeader(header), cellIndex);
                }
            }
            if (isRosterSheet(headerIndex)) {
                return new ResolvedHeader(rowIndex, headerIndex);
            }
        }
        return null;
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

    private String readRequiredCell(
        Row row,
        Map<String, Integer> headerIndex,
        DataFormatter formatter,
        String primaryHeader,
        String... aliases
    ) {
        String value = readOptionalCell(row, headerIndex, formatter, primaryHeader, aliases);
        if (value == null || value.isBlank()) {
            throw new BizException("FEISHU_FILE_IMPORT_FORMAT_INVALID", "飞书花名册中存在必填列空值");
        }
        return value;
    }

    private String readOptionalCell(
        Row row,
        Map<String, Integer> headerIndex,
        DataFormatter formatter,
        String primaryHeader,
        String... aliases
    ) {
        Integer index = headerIndex.get(normalizeHeader(primaryHeader));
        if (index == null) {
            for (String alias : aliases) {
                index = headerIndex.get(normalizeHeader(alias));
                if (index != null) {
                    break;
                }
            }
        }
        if (index == null || row.getCell(index) == null) {
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
        List<Path> rootPaths = resolveAllowedRootPaths();
        Path candidatePath = Paths.get(sanitizedDocumentPath);
        Path resolvedPath = candidatePath.isAbsolute()
            ? candidatePath.toAbsolutePath().normalize()
            : selectPreferredRoot(rootPaths).resolve(candidatePath).normalize();
        boolean allowed = rootPaths.stream().anyMatch(rootPath -> resolvedPath.startsWith(rootPath));
        if (!allowed) {
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

    private List<Path> resolveAllowedRootPaths() {
        Path configuredPath = Paths.get(properties.getRootDir());
        if (configuredPath.isAbsolute()) {
            return List.of(configuredPath.toAbsolutePath().normalize());
        }

        List<Path> candidates = new ArrayList<>();
        Path workingDirectory = Paths.get("").toAbsolutePath().normalize();
        candidates.add(workingDirectory.resolve(configuredPath).normalize());

        Path applicationDirectory = new ApplicationHome(FeishuImportDocumentResolver.class).getDir()
            .toPath()
            .toAbsolutePath()
            .normalize();
        candidates.add(applicationDirectory.resolve(configuredPath).normalize());
        if (applicationDirectory.getParent() != null) {
            candidates.add(applicationDirectory.getParent().resolve(configuredPath).normalize());
        }
        if (applicationDirectory.getParent() != null && applicationDirectory.getParent().getParent() != null) {
            candidates.add(applicationDirectory.getParent().getParent().resolve(configuredPath).normalize());
        }
        return candidates.stream().distinct().toList();
    }

    private Path selectPreferredRoot(List<Path> rootPaths) {
        return rootPaths.stream()
            .filter(Files::exists)
            .findFirst()
            .orElse(rootPaths.get(0));
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

    private record RosterUserRow(
        String externalId,
        String username,
        String realName,
        String email,
        String mobile,
        String employeeNo,
        String mainDepartmentExternalId,
        List<String> partTimeDepartmentExternalIds,
        Integer status,
        Integer orderNo
    ) {
    }

    private record ResolvedHeader(
        int headerRowIndex,
        Map<String, Integer> headerIndex
    ) {
    }
}
