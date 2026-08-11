package com.company.idm.interfaces.v2.user;

import java.util.List;

/**
 * V2 用户分页查询参数（@ModelAttribute 绑定，须为带 setter 的 POJO，不能是 record）。
 * 精确字段用 eq、模糊字段用 like、部门用 department_rules 规则 JSON。
 */
public class UserV2PageQuery {

    /** 综合关键词（跨 userId/realName/employeeNo 模糊） */
    private String keyword;
    /** 精确用户ID */
    private String userId;
    /** 姓名（模糊） */
    private String realName;
    /** 工号（模糊） */
    private String employeeNo;
    /** 手机（模糊） */
    private String mobile;
    /** 邮箱（模糊） */
    private String email;
    /** 内网邮箱（模糊） */
    private String intranetEmail;
    /** 职务（模糊） */
    private String jobTitle;
    /** 允许使用 */
    private Boolean accessAllowed;
    /** 在职状态 ACTIVE/RESIGNED */
    private String employmentStatus;
    /** 账号状态 */
    private String accountStatus;
    /** 来源 MANUAL/FEISHU */
    private String sourceType;
    /** 角色编码多选 */
    private List<String> roleCodes;
    /** 创建时间起（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss） */
    private String createdStart;
    /** 创建时间止（含当天，按[起,止]闭区间处理） */
    private String createdEnd;
    /** 部门规则 JSON 数组字符串（前端序列化），结构同 lumen_flow */
    private String departmentRules;
    private long pageNum = 1;
    private long pageSize = 10;
    private String orderBy;
    private String orderDir;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIntranetEmail() {
        return intranetEmail;
    }

    public void setIntranetEmail(String intranetEmail) {
        this.intranetEmail = intranetEmail;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public Boolean getAccessAllowed() {
        return accessAllowed;
    }

    public void setAccessAllowed(Boolean accessAllowed) {
        this.accessAllowed = accessAllowed;
    }

    public String getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(String employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public List<String> getRoleCodes() {
        return roleCodes;
    }

    public void setRoleCodes(List<String> roleCodes) {
        this.roleCodes = roleCodes;
    }

    public String getCreatedStart() {
        return createdStart;
    }

    public void setCreatedStart(String createdStart) {
        this.createdStart = createdStart;
    }

    public String getCreatedEnd() {
        return createdEnd;
    }

    public void setCreatedEnd(String createdEnd) {
        this.createdEnd = createdEnd;
    }

    public String getDepartmentRules() {
        return departmentRules;
    }

    public void setDepartmentRules(String departmentRules) {
        this.departmentRules = departmentRules;
    }

    public long getPageNum() {
        return pageNum;
    }

    public void setPageNum(long pageNum) {
        this.pageNum = pageNum;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    public String getOrderDir() {
        return orderDir;
    }

    public void setOrderDir(String orderDir) {
        this.orderDir = orderDir;
    }
}
