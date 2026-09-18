package com.company.idm.domain.rolegroup;

/**
 * 角色供给事件记录器端口。
 *
 * <p>所有改变对外授权事实的写入点（成员加入/退出、用户停用与删除、角色目录变化、
 * 角色组展示信息变化、订阅生命周期控制）都必须经此端口记录事件，且与业务变更处于
 * 同一事务：回滚时事件一并消失，提交后即使进程崩溃也能由发布器继续投递。
 * 范围版本分配由实现完成，调用方无需感知。
 */
public interface RoleSupplyEventRecorder {

    /**
     * 记录一条角色供给事件。
     *
     * @param draft 事件草稿，不可为空且必须包含事件类型
     * @return 事件稳定标识，供消费方幂等
     */
    Long record(RoleSupplyEventDraft draft);
}
