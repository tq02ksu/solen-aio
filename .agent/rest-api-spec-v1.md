# Housekeeper REST API 设计规范

## 唯一可信源
`openapi.yaml` 是接口文档的唯一可信源，所有接口定义必须写入此文件。

---

## 认证方式
- 请求头：`Authorization: {sessionId}`
- 例外路径：`/api/wx/**` 和 `/actuator/**` 无需认证

---

## HTTP 状态码约定

| 状态码 | 含义 |
|--------|------|
| 200 | 查询/修改成功 |
| 201 | 创建成功 |
| 204 | 删除成功 |
| 400 | 参数错误 |
| 401 | 未登录 |
| 403 | 权限不足 |
| 500 | 服务器异常 |

---

## 业务错误码（10000-10010）

| 错误码 | 说明 |
|--------|------|
| 10000 | 必填字段为空 |
| 10001 | 字段 True/false 不符 |
| 10002 | 字段范围不符 |
| 10003 | 整数位或小数位超过上限 |
| 10004 | 字段格式不正确 |
| 10005 | 时间范围不符（应为过去或现在） |
| 10006 | 时间范围不符（应为将来或现在） |
| 10007 | 字符串长度不符合要求 |
| 10008 | 不允许设置的字段有值 |
| 10009 | 数量超过限制 |
| 10010 | 与已有数据重复 |

---

## RESTful 路径规范

### 资源命名
- **使用复数名词**：`/projects`, `/employees`, `/invitations`
- **嵌套资源**：`/project-finances/{id}/start-sign-task`
- **查询专用**：`/projects/query`（复杂条件查询用 POST）

### HTTP 方法语义

| 方法 | 用法 |
|------|------|
| GET | 查询列表/详情 |
| POST | 创建资源 |
| PUT | 更新资源 |
| DELETE | 删除资源 |

### 示例
```
GET    /projects              # 查询列表
POST   /projects              # 创建
GET    /projects/{id}         # 查询详情
PUT    /projects/{id}         # 更新
DELETE /projects/{id}         # 删除
POST   /projects/query        # 复杂条件查询
POST   /project-workloads/{id}/start-sign-task  # 业务动作
```

---

## 分页查询规范

### 请求参数
```yaml
PageableRequest:
  properties:
    page:  integer  # 页码，从0开始，默认0
    size:  integer  # 每页数量，默认10
    sort:  string   # 排序字段（支持多字段：field1,field2）
    order: string   # asc | desc，默认desc
```

### 响应格式
```yaml
PageableResponse:
  properties:
    total: integer  # 总记录数
    data:  array    # 数据列表
```

---

## BO 对象命名规范

| 命名模式 | 用途 | 示例 |
|----------|------|------|
| `*BasicBO` | 基础/简要信息（用于列表展示） | `ProjectBasicBO`, `EmployeeBasicBO` |
| `*BO` | 完整业务对象（用于详情展示） | `ProjectBO`, `EmployeeBO` |
| `*QueryRequest` | 查询请求参数 | `ProjectQueryRequest` |
| `*PageResponse` | 分页响应（含汇总） | `FinancialReportPageResponse` |

### 列表与详情的字段规范

- **列表接口**（GET /xxx）：返回概要字段，仅包含识别和筛选所需的最小字段集
- **详情接口**（GET /xxx/{id}）：返回完整字段，包括关联对象、金额汇总、状态等全部信息

示例：
```
GET /projects              → 返回 id, name, status（概要）
GET /projects/{id}         → 返回全部字段（完整）
```

---

## 文件上传规范

- 使用 `multipart/form-data`
- 文件类型通过 `fileType` 字段区分：

| fileType | 说明 |
|----------|------|
| 39 | 身份证正面 |
| 40 | 身份证反面 |
| 41 | 银行卡正面 |
| 42 | 银行卡反面 |
| 43 | 工作日志图片 |
| 44 | 工作日志视频 |
| 45 | 合同 |
| 46 | 承诺书 |
| 47 | 保险单 |

---

## OpenAPI Tags 组织

按业务模块分组接口：

| Tag | 描述 |
|-----|------|
| Auth | 登录、会话校验、实名认证 |
| Files | 文件上传 |
| Departments | 劳务公司管理 |
| Users | 员工查询 |
| Projects | 工单管理 |
| Structures | 用工关系管理 |
| Invitations | 邀请管理 |
| Docs | 协议签署 |
| Worklogs | 工作日志/验收单 |
| Payroll | 工资表管理 |
| Accounts | 账户管理 |
| Reports | 报表中心 |
| Bank | 银行卡查询 |
| Fadada | 法大大回调 |

---

## 新增接口流程

1. 在 `openapi.yaml` 中定义 schema 和 path
2. 在 `housekeeper-api` 中定义 Controller 接口
3. 在 `housekeeper-app` 中实现 ControllerImpl
4. 确保 openapi.yaml 与代码实现保持一致

---

## 角色定义

| 角色代码 | 说明 |
|----------|------|
| ADMIN | 超级管理员 |
| PROJECT_ADM | 工单管理员 |
| DEFAULT_ADM | 违约管理员 |
| CONTRACT_ADM | 合同管理员 |
| FOREMAN | 工长 |
| PROJECT_ACTUAL_CONTROLLER | 项目实控人 |
| PROJECT_MANAGER | 项目负责人 |
| LABOR_CONTRACTOR | 大清负责人 |
| SUB_ITEM_MANAGER | 单项负责人 |
| TEAM_LEADER | 班组长 |
| WORKER | 工人 |
| QUALITY_ADM | 合格单管理员 |
| ACCOUNTANT | 财务管理员 |

---

## 项目类型

| 类型代码 | 说明 |
|----------|------|
| CONSTRUCTION | 新建 |
| RENOVATION | 改造 |
| MAINTENANCE | 维修 |