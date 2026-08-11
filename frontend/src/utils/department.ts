import type { DepartmentTreeNode, DepartmentTreeOption } from '@/types/department'

/** 将部门树转换为 el-tree-select / el-cascader 可用的选项（停用节点置灰）。 */
export function buildDepartmentOptions(nodes: DepartmentTreeNode[]): DepartmentTreeOption[] {
  return nodes.map((item) => ({
    value: item.deptCode,
    label: `${item.deptName} (${item.deptCode})`,
    disabled: item.status !== 1,
    children: item.children?.length ? buildDepartmentOptions(item.children) : undefined,
  }))
}
