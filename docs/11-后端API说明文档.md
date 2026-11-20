# SmartOrder 校园点单系统 API 说明文档

# V1 . 0. 0

## SmartOrder 项目组

## (版权所有，翻版必究)

## 目录

1. 项目说明
   1.1 服务器地址
   1.2 登录说明
   1.3 注销说明
   1.4 安全认证
   1.5 系统默认用户
   1.6 表格分页
   1.7 系统值和返回状态码
2. 接口列表
   2.1 认证相关接口
   2.2 控制台认证相关接口
   2.3 商家与菜单查询接口
   2.4 自提点相关接口
   2.5 购物车相关接口
   2.6 订单相关接口
   2.7 支付相关接口
   2.8 退款相关接口
   2.9 优惠券相关接口
   2.10 评价相关接口
   2.11 通知相关接口
   2.12 地址相关接口
   2.13 收藏相关接口
   2.14 实时推送相关接口
   2.15 系统相关接口
   2.16 商家控制台相关接口
   2.17 平台管理相关接口
   2.18 平台用户管理相关接口
   2.19 配送员相关接口

# 1.项目说明

# 1.1服务器地址

本机开发环境：`http://localhost:8080`

所有接口统一前缀 `/api/v1`，前端开发服务器通过代理把 `/api/v1` 转发到上述地址。非本机访问须使用环境维护者提供的实际地址。

# 1.2登录说明

系统存在三套账号主体，登录入口不同：

- 学生（USER 主体）：`POST /api/v1/auth/password/login` 或 `POST /api/v1/auth/sms/verify`。
- 商家与平台账号（ACCOUNT 主体）：`POST /api/v1/auth/console/login`。
- 配送员：`POST /api/v1/auth/rider/login`。

登录成功后返回 `accessToken` 与 `refreshToken`。`accessToken` 有效期 2 小时，用于接口鉴权；`refreshToken` 有效期 30 天，用于调用 `/api/v1/auth/refresh` 换取新的 accessToken。处于安全考虑，部分接口需要先登录获取授权 TOKEN 才能调用，需要认证的接口在每个接口的详细说明中标出。

# 1.3注销说明

学生退出登录：`POST /api/v1/auth/logout`，请求体可带 `refreshToken`，无需 Authorization 头。控制台账号与配送员当前无独立注销接口，客户端丢弃本地 token 即可。

# 1.4安全认证

除白名单接口外，所有接口须在请求头携带认证信息，格式如下：参数 `Authorization`，参数值 `Bearer <accessToken>`。

SSE 事件流 `GET /api/v1/events/stream` 在白名单内，通过 query 参数 `token` 传递 accessToken，也支持 `Authorization` 头。

# 1.5系统默认用户

测试账号见 `docs/07-测试账号与登录说明.md`。学生账号与控制台账号是两套独立体系，不能混用。学生初始密码、控制台统一密码均以迁移脚本与环境配置为准。

**TODO(上线前)：删除或改密全部演示账号，替换控制台占位密码哈希并清空开发统一密码配置。**

# 1.6表格分页

返回列表数据的接口涉及分页时，参数格式如下：参数 `page` 当前页码（从 1 开始），参数 `pageSize` 每页数据条数。部分接口参数名为 `pageNum`。分页返回结构为 `list`、`total`、`page`、`pageSize`。

# 1.7系统值和返回状态码

本文档所有接口返回值类型如不特殊说明均为 JSON 格式。所有业务接口统一使用 `ApiResponse<T>` 包装，结构如下：

| 字段名 | 字段含义 | 类型 |
| --- | --- | --- |
| code | 业务状态码，0 表示成功 | integer |
| message | 结果说明 | string |
| data | 业务数据，无数据时为 null | object |
| traceId | 链路追踪编号 | string |

HTTP 状态与业务 code 的关系：业务失败多数以 HTTP 200 返回并在 `code` 中给出具体原因；鉴权失败返回 401，权限不足返回 403，资源不存在返回 404，系统异常返回 500。

| 状态码 | 说明 |
| --- | --- |
| 200 | 正常 |
| 500 | 系统异常 |
| 401 | 未授权 |
| 403 | 禁止访问 |
| 404 | 未找到资源 |

主要业务错误码如下：

| code | 说明 |
| --- | --- |
| 0 | 成功 |
| 10000 | 系统异常 |
| 10001 | 参数不正确 |
| 10002 | 请求的资源不存在 |
| 10005 | 操作太频繁 |
| 20000 | 请先登录 |
| 20001 | 登录已过期 |
| 20002 | 登录凭证无效 |
| 20003 | 没有权限执行该操作 |
| 20004 | 账号已被停用 |
| 20005 | 请先完成学生身份认证 |
| 20008 | 学号或密码不正确 |
| 20009 | 该学号已注册 |
| 20020 | 账号或密码不正确 |
| 30001 | 档口不存在 |
| 30002 | 档口当前不营业 |
| 30010 | 菜品不存在或已下架 |
| 30020 | 库存不够了 |
| 30030 | 购物车是空的 |
| 30100 | 订单不存在 |
| 30102 | 订单当前状态不支持该操作 |
| 30103 | 订单已被其他操作更新 |
| 30107 | 取餐码不正确或已被核销 |
| 30108 | 缺少幂等键 |
| 40001 | 该支付方式未开放 |
| 40005 | 订单已支付 |

金额相关字段一律为整数，单位为分，例如 1250 表示 12.50 元。时间字段格式为 `yyyy-MM-dd HH:mm:ss`，日期字段格式为 `yyyy-MM-dd`。

# 2.接口列表

# 2.1认证相关接口

# 2.1.1密码登录

### 接口地址

POST /api/v1/auth/password/login

### 接口描述

学生使用学号与密码登录，返回学生会话令牌与用户信息。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "studentNo": "2022010101",
  "password": "010101"
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| studentNo | 学号，8 至 12 位数字 | body | true | string |  |
| password | 密码 | body | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| accessToken | string | 访问令牌 |  |
| refreshToken | string | 刷新令牌 |  |
| expiresIn | integer | accessToken 有效期，秒 |  |
| user | object | 用户资料 |  |
| user.userId | string | 用户 ID |  |
| user.nickname | string | 昵称 |  |
| user.avatarUrl | string | 头像地址 |  |
| user.studentNo | string | 学号 |  |
| user.phone | string | 手机号 |  |
| user.verifyStatus | integer | 学籍审核状态 |  |
| user.creditScore | integer | 信用分 |  |
| user.orderCount | integer | 订单数 |  |

### 响应示例

```
{
  "code": 0,
  "message": "ok",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200,
    "user": {
      "userId": "1",
      "nickname": "张狗蛋",
      "avatarUrl": "",
      "studentNo": "2022010101",
      "phone": "13800000001",
      "verifyStatus": 2,
      "creditScore": 100,
      "orderCount": 3
    }
  },
  "traceId": "8f3c1a2b"
}
```

# 2.1.2注册

### 接口地址

POST /api/v1/auth/register

### 接口描述

学生注册，注册后学籍状态为待审核，返回会话令牌。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "studentNo": "2022010199",
  "password": "123456",
  "nickname": "新同学"
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| studentNo | 学号，8 至 12 位数字 | body | true | string |  |
| password | 密码，至少 6 位 | body | true | string |  |
| nickname | 昵称，可空 | body | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |

### 响应参数

同 2.1.1 密码登录。

# 2.1.3发送短信验证码

### 接口地址

POST /api/v1/auth/sms/send

### 接口描述

向指定手机号发送登录验证码，开发环境验证码固定为 000000，有效期 5 分钟。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "phone": "13800000001"
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| phone | 手机号 | body | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| expireSeconds | integer | 验证码有效期，秒 |  |

### 响应示例

```
{
  "code": 0,
  "message": "ok",
  "data": { "expireSeconds": 300 },
  "traceId": "8f3c1a2b"
}
```

# 2.1.4短信验证码登录

### 接口地址

POST /api/v1/auth/sms/verify

### 接口描述

使用手机号与验证码登录，手机号未注册时自动创建用户。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "phone": "13800000001",
  "code": "000000"
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| phone | 手机号 | body | true | string |  |
| code | 验证码 | body | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |

### 响应参数

同 2.1.1 密码登录。

# 2.1.5获取当前用户信息

### 接口地址

GET /api/v1/auth/profile

### 接口描述

获取当前登录学生的资料。需要认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| userId | string | 用户 ID |  |
| nickname | string | 昵称 |  |
| avatarUrl | string | 头像地址 |  |
| phone | string | 手机号 |  |
| realName | string | 真实姓名 |  |
| studentNo | string | 学号 |  |
| college | string | 学院 |  |
| campusArea | string | 校区 |  |
| dormBuilding | string | 宿舍楼 |  |
| verifyStatus | integer | 学籍审核状态 |  |
| verifyNote | string | 审核说明 |  |
| creditScore | integer | 信用分 |  |
| orderCount | integer | 订单数 |  |

# 2.1.6刷新令牌

### 接口地址

POST /api/v1/auth/refresh

### 接口描述

使用 refreshToken 换取新的 accessToken 与 refreshToken，学生与账号主体均支持。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| refreshToken | 刷新令牌 | body | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| accessToken | string | 新的访问令牌 |  |
| refreshToken | string | 新的刷新令牌 |  |
| expiresIn | integer | accessToken 有效期，秒 |  |

# 2.1.7退出登录

### 接口地址

POST /api/v1/auth/logout

### 接口描述

注销 refreshToken，使其失效。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| refreshToken | 刷新令牌，可空 | body | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |

### 响应参数

data 为 null。

# 2.2控制台认证相关接口

# 2.2.1控制台登录

### 接口地址

POST /api/v1/auth/console/login

### 接口描述

商家与平台账号登录，返回控制台会话令牌与账号信息。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "username": "chuanxiang",
  "password": "demo123456"
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| username | 账号 | body | true | string |  |
| password | 密码 | body | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| accessToken | string | 访问令牌 |  |
| refreshToken | string | 刷新令牌 |  |
| expiresIn | integer | accessToken 有效期，秒 |  |
| account | object | 账号资料 |  |
| account.accountId | string | 账号 ID |  |
| account.username | string | 账号 |  |
| account.realName | string | 真实姓名 |  |
| account.roleCode | string | 角色编码 |  |
| account.roleName | string | 角色名称 |  |
| account.merchantId | string | 所属档口 ID |  |
| account.status | integer | 账号状态 |  |

### 响应示例

```
{
  "code": 0,
  "message": "ok",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200,
    "account": {
      "accountId": "5",
      "username": "chuanxiang",
      "realName": "川香负责人",
      "roleCode": "MERCHANT_OWNER",
      "roleName": "商家负责人",
      "merchantId": "1",
      "status": 1
    }
  },
  "traceId": "8f3c1a2b"
}
```

# 2.3商家与菜单查询接口

# 2.3.1查询商家列表

### 接口地址

GET /api/v1/merchants

### 接口描述

查询档口列表，可按校区筛选或只查营业中的档口。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| campusArea | 校区，可空 | query | false | string |  |
| openOnly | 只返回营业中的档口 | query | false | boolean |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |

### 响应参数

data 为数组，元素字段如下：

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| merchantId | string | 档口 ID |  |
| name | string | 档口名称 |  |
| categoryCode | string | 品类编码 |  |
| categoryName | string | 品类名称 |  |
| logoUrl | string | 标志图地址 |  |
| bannerUrl | string | 横幅图地址 |  |
| description | string | 简介 |  |
| pickupPointName | string | 自提点名称 |  |
| campusArea | string | 校区 |  |
| minOrderAmount | integer | 起送金额，分 |  |
| packFee | integer | 打包费，分 |  |
| avgPrepareMinutes | integer | 平均出餐分钟数 |  |
| monthSold | integer | 月销量 |  |
| ratingScore | integer | 评分 |  |
| ratingCount | integer | 评价数 |  |
| status | integer | 档口状态 |  |
| open | boolean | 是否营业 |  |
| openTime | string | 营业开始时间 |  |
| closeTime | string | 营业结束时间 |  |
| acceptStart | string | 接单开始时间 |  |
| acceptEnd | string | 接单结束时间 |  |
| closedToday | boolean | 今日是否临时打烊 |  |
| closeReason | string | 打烊原因 |  |

# 2.3.2查询商家详情

### 接口地址

GET /api/v1/merchants/{merchantId}

### 接口描述

查询单个档口概要信息。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| merchantId | 档口 ID | path | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 404 | Not Found |  |

### 响应参数

字段同 2.3.1 元素。

# 2.3.3查询商家菜单

### 接口地址

GET /api/v1/merchants/{merchantId}/menu

### 接口描述

查询档口完整菜单，含分类、菜品、规格组与当日库存。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| merchantId | 档口 ID | path | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 404 | Not Found |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| merchant | object | 档口概要，见 2.3.1 |  |
| categories | array | 分类列表 |  |
| categories[].categoryId | string | 分类 ID |  |
| categories[].name | string | 分类名称 |  |
| categories[].sortWeight | integer | 排序权重 |  |
| categories[].dishes | array | 菜品列表 |  |
| serverTime | string | 服务器时间 |  |
| stockVersion | string | 库存版本号 |  |

菜品对象字段：

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| dishId | string | 菜品 ID |  |
| name | string | 菜品名称 |  |
| subtitle | string | 副标题 |  |
| imageUrl | string | 图片地址 |  |
| basePrice | integer | 基础价，分 |  |
| packFee | integer | 打包费，分 |  |
| minBuyCount | integer | 起购数量 |  |
| maxBuyPerOrder | integer | 单单限购 |  |
| monthSold | integer | 月销量 |  |
| tags | array | 标签 |  |
| recommended | boolean | 是否推荐 |  |
| status | integer | 状态 |  |
| groups | array | 规格组列表 |  |
| stock | object | 库存信息 |  |

规格组字段：`groupId`、`name`、`groupType`、`required`、`minSelect`、`maxSelect`、`options`；规格项字段：`optionId`、`groupId`、`name`、`priceDelta`、`isDefault`、`stock`、`soldOut`。库存字段：`dishId`、`bizDate`、`total`、`locked`、`sold`、`remaining`、`soldOut`、`trackStock`。

# 2.3.4查询菜品详情

### 接口地址

GET /api/v1/dishes/{dishId}

### 接口描述

查询单个菜品详情，含规格组、库存、所属档口与分类名称。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| dishId | 菜品 ID | path | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 404 | Not Found |  |

### 响应参数

字段同 2.3.3 菜品对象，另含 `merchantName`、`categoryName`。

# 2.3.5搜索

### 接口地址

GET /api/v1/search

### 接口描述

按关键字与校区搜索菜品与档口。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| keyword | 搜索关键字，可空 | query | false | string |  |
| campusArea | 校区，可空 | query | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |

### 响应参数

data 为数组，元素字段：`merchantId`、`merchantName`、`merchantOpen`、`campusArea`、`categoryName`、`dish`（菜品对象）。

# 2.4自提点相关接口

# 2.4.1查询自提点列表

### 接口地址

GET /api/v1/pickup-points

### 接口描述

查询自提点列表，可按校区筛选。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| campusArea | 校区，可空 | query | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |

### 响应参数

data 为数组，元素字段：

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| pickupPointId | string | 自提点 ID |  |
| name | string | 名称 |  |
| campusArea | string | 校区 |  |
| building | string | 楼栋 |  |
| floorText | string | 楼层说明 |  |
| openTime | string | 开放时间 |  |
| closeTime | string | 关闭时间 |  |

# 2.5购物车相关接口

# 2.5.1查询购物车

### 接口地址

GET /api/v1/cart

### 接口描述

查询当前用户购物车，按档口分组。需要认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| groups | array | 按档口分组的购物车项 |  |
| groups[].merchantId | string | 档口 ID |  |
| groups[].merchantName | string | 档口名称 |  |
| groups[].minOrderAmount | integer | 起送金额，分 |  |
| groups[].packFee | integer | 打包费，分 |  |
| groups[].open | boolean | 是否营业 |  |
| groups[].items | array | 购物车项列表 |  |
| groups[].itemCount | integer | 商品件数 |  |
| groups[].amountBeforeDiscount | integer | 优惠前金额，分 |  |
| groups[].gapToMinimum | integer | 距离起送差额，分 |  |
| selectedCount | integer | 选中件数 |  |
| selectedAmount | integer | 选中金额，分 |  |
| serverTime | string | 服务器时间 |  |

购物车项字段：`cartItemId`、`merchantId`、`merchantName`、`dishId`、`dishName`、`dishImage`、`basePrice`、`packFee`、`entries`、`optionText`、`optionHash`、`unitPrice`、`quantity`、`lineAmount`、`selected`、`remaining`、`trackStock`、`maxBuyPerOrder`。其中 `entries` 元素字段：`group`、`optionId`、`name`、`priceDelta`。

# 2.5.2添加商品

### 接口地址

POST /api/v1/cart/items

### 接口描述

向购物车添加菜品。需要认证。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "merchantId": "1",
  "dishId": "11",
  "entries": [
    { "group": "规格", "optionId": "111", "name": "大份", "priceDelta": 200 }
  ],
  "quantity": 2
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| merchantId | 档口 ID | body | true | string |  |
| dishId | 菜品 ID，为空时返回当前购物车 | body | false | string |  |
| entries | 所选规格数组 | body | false | array |  |
| quantity | 数量，默认 1 | body | false | integer |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

同 2.5.1 查询购物车。

# 2.5.3修改购物车项

### 接口地址

PATCH /api/v1/cart/items/{cartItemId}

### 接口描述

修改购物车项数量或选中状态。需要认证。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "quantity": 3,
  "selected": true
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| cartItemId | 购物车项 ID | path | true | string |  |
| quantity | 数量 | body | false | integer |  |
| selected | 是否选中 | body | false | boolean |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

同 2.5.1 查询购物车。

# 2.5.4清空购物车

### 接口地址

DELETE /api/v1/cart

### 接口描述

清空当前用户购物车，可只清空指定档口。需要认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| merchantId | 档口 ID，为空时清空全部 | query | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

同 2.5.1 查询购物车。

# 2.6订单相关接口

# 2.6.1下单预检

### 接口地址

POST /api/v1/orders/preflight

### 接口描述

校验营业状态、库存、起送金额与优惠券，返回金额试算，不产生订单。需要认证。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "merchantId": "1",
  "items": [
    { "dishId": "11", "entries": [], "quantity": 1 }
  ],
  "cartItemIds": [],
  "remark": "",
  "fulfillmentType": "PICKUP",
  "couponId": ""
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| merchantId | 档口 ID | body | true | string |  |
| items | 菜品行数组，与 cartItemIds 二选一 | body | false | array |  |
| items[].dishId | 菜品 ID | body | true | string |  |
| items[].entries | 所选规格数组 | body | false | array |  |
| items[].quantity | 数量 | body | false | integer |  |
| cartItemIds | 购物车项 ID 数组 | body | false | array |  |
| remark | 备注 | body | false | string |  |
| contactName | 联系人姓名 | body | false | string |  |
| contactPhone | 联系人电话 | body | false | string |  |
| fulfillmentType | 履约方式，PICKUP 或 DELIVERY，默认 PICKUP | body | false | string |  |
| addressId | 配送地址 ID | body | false | string |  |
| couponId | 优惠券 ID | body | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| merchantId | string | 档口 ID |  |
| merchantName | string | 档口名称 |  |
| lines | array | 菜品行，见下方 |  |
| itemAmount | integer | 菜品小计，分 |  |
| packFee | integer | 打包费，分 |  |
| deliveryFee | integer | 配送费，分 |  |
| discountAmount | integer | 优惠金额，分 |  |
| payAmount | integer | 应付金额，分 |  |
| minOrderAmount | integer | 起送金额，分 |  |
| gapToMinimum | integer | 距离起送差额，分 |  |
| fulfillable | boolean | 是否可下单 |  |
| rejectReason | string | 不可下单原因 |  |
| serverTime | string | 服务器时间 |  |
| expireAt | string | 下单有效期 |  |
| couponId | string | 优惠券 ID |  |
| couponName | string | 优惠券名称 |  |
| couponRejected | string | 优惠券不可用原因 |  |

菜品行字段：`dishId`、`dishName`、`entries`、`unitPrice`、`quantity`、`lineAmount`、`remaining`。

# 2.6.2创建订单

### 接口地址

POST /api/v1/orders

### 接口描述

创建订单。必须在请求头携带 `X-Client-Order-No` 作为幂等键，缺失时返回 30108。需要认证。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求头

| 参数名称 | 参数说明 | 必须 | 数据类型 |
| --- | --- | --- | --- |
| X-Client-Order-No | 客户端生成的幂等键 | true | string |

### 请求示例

```
{
  "merchantId": "1",
  "cartItemIds": ["21", "22"],
  "remark": "少辣",
  "fulfillmentType": "PICKUP",
  "couponId": ""
}
```

### 请求参数

字段同 2.6.1 下单预检。

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| orderNo | string | 订单号 |  |
| clientOrderNo | string | 客户端幂等键 |  |
| status | string | 订单状态，初始为 PENDING_PAYMENT |  |
| payAmount | integer | 应付金额，分 |  |
| expireAt | string | 支付截止时间 |  |
| serverTime | string | 服务器时间 |  |

### 响应示例

```
{
  "code": 0,
  "message": "ok",
  "data": {
    "orderNo": "20261120143000000001",
    "clientOrderNo": "c7d21a",
    "status": "PENDING_PAYMENT",
    "payAmount": 1250,
    "expireAt": "2026-11-20 14:40:00",
    "serverTime": "2026-11-20 14:30:00"
  },
  "traceId": "8f3c1a2b"
}
```

# 2.6.3查询订单列表

### 接口地址

GET /api/v1/orders

### 接口描述

查询当前用户的订单列表。需要认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| status | 状态筛选，可空 | query | false | string |  |
| page | 当前页码，默认 1 | query | false | integer |  |
| pageSize | 每页条数，默认 10 | query | false | integer |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

data 为分页对象：`list`、`total`、`page`、`pageSize`。列表元素字段：

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| orderNo | string | 订单号 |  |
| merchantId | string | 档口 ID |  |
| merchantName | string | 档口名称 |  |
| pickupPointName | string | 自提点名称 |  |
| status | string | 订单状态 |  |
| payAmount | integer | 应付金额，分 |  |
| itemCount | integer | 商品件数 |  |
| pickupCode | string | 取餐码 |  |
| bizDate | string | 营业日期 |  |
| mealPeriod | string | 餐段 |  |
| createdAt | string | 创建时间 |  |
| expireAt | string | 支付截止时间 |  |
| expectTakeAt | string | 预计取餐时间 |  |
| pickupDeadlineAt | string | 取餐截止时间 |  |
| serverTime | string | 服务器时间 |  |

# 2.6.4查询订单详情

### 接口地址

GET /api/v1/orders/{orderNo}

### 接口描述

查询订单详情，含菜品行、状态时间线、支付与退款信息。需要认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| orderNo | 订单号 | path | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 404 | Not Found |  |

### 响应参数

在 2.6.3 列表字段基础上，另含：

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| clientOrderNo | string | 客户端幂等键 |  |
| fulfillmentType | string | 履约方式 |  |
| itemAmount | integer | 菜品小计，分 |  |
| packFee | integer | 打包费，分 |  |
| deliveryFee | integer | 配送费，分 |  |
| discountAmount | integer | 优惠金额，分 |  |
| contactName | string | 联系人姓名 |  |
| contactPhone | string | 联系人电话 |  |
| remark | string | 备注 |  |
| paidAt | string | 支付时间 |  |
| acceptedAt | string | 接单时间 |  |
| readyAt | string | 出餐时间 |  |
| finishedAt | string | 完成时间 |  |
| cancelledAt | string | 取消时间 |  |
| cancelReason | string | 取消原因 |  |
| cancelBy | string | 取消方 |  |
| version | integer | 乐观锁版本 |  |
| items | array | 菜品行列表 |  |
| timeline | array | 状态时间线 |  |
| payment | object | 支付信息 |  |
| refund | object | 退款信息 |  |
| cancelWindowEndsAt | string | 可取消截止时间 |  |

菜品行字段：`itemId`、`dishId`、`dishName`、`dishImage`、`categoryName`、`optionText`、`unitPrice`、`originUnitPrice`、`packFee`、`discountAmount`、`quantity`、`lineAmount`。时间线字段：`fromStatus`、`toStatus`、`operatorType`、`operatorName`、`note`、`createdAt`。支付信息字段：`paymentNo`、`orderNo`、`channel`、`status`、`payAmount`、`paidAt`、`createdAt`、`expireAt`。退款信息字段：`refundNo`、`orderNo`、`refundAmount`、`status`、`reason`、`auditNote`、`createdAt`、`auditedAt`、`succeededAt`。

# 2.6.5取消订单

### 接口地址

POST /api/v1/orders/{orderNo}/cancel

### 接口描述

取消尚未进入不可取消状态的订单。需要认证。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "reason": "临时有事"
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| orderNo | 订单号 | path | true | string |  |
| reason | 取消原因，可空 | body | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

同 2.6.4 查询订单详情。

# 2.7支付相关接口

# 2.7.1创建支付单

### 接口地址

POST /api/v1/payments

### 接口描述

为待支付订单创建支付单，当前开放 MOCK 渠道。需要认证。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "orderNo": "20261120143000000001",
  "channel": "MOCK"
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| orderNo | 订单号 | body | true | string |  |
| channel | 支付渠道，当前为 MOCK | body | true | string |  |
| clientIp | 客户端 IP，缺省时取请求来源 IP | body | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| paymentNo | string | 支付单号 |  |
| channel | string | 支付渠道 |  |
| status | string | 支付单状态 |  |
| payUrl | string | 支付地址 |  |
| form | object | 提交支付所需参数 |  |
| expireAt | string | 支付单过期时间 |  |
| serverTime | string | 服务器时间 |  |

# 2.7.2确认支付

### 接口地址

POST /api/v1/payments/{paymentNo}/confirm

### 接口描述

模拟渠道回调，确认支付成功并推进订单状态。需要认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| paymentNo | 支付单号 | path | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| paymentNo | string | 支付单号 |  |
| orderNo | string | 订单号 |  |
| channel | string | 支付渠道 |  |
| status | string | 支付单状态 |  |
| payAmount | integer | 支付金额，分 |  |
| paidAt | string | 支付时间 |  |
| createdAt | string | 创建时间 |  |
| expireAt | string | 过期时间 |  |

# 2.7.3查询支付单

### 接口地址

GET /api/v1/payments/{paymentNo}

### 接口描述

查询支付单当前状态。需要认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| paymentNo | 支付单号 | path | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 404 | Not Found |  |

### 响应参数

同 2.7.2 确认支付。

# 2.8退款相关接口

# 2.8.1审核退款

### 接口地址

POST /api/v1/refunds/{refundNo}/audit

### 接口描述

商家负责人审核退款申请。仅 ACCOUNT 主体且具备商家负责人角色可调用，MERCHANT_STAFF 返回 403。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "approved": true,
  "note": "同意退款"
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| refundNo | 退款单号 | path | true | string |  |
| approved | 是否通过 | body | true | boolean |  |
| note | 审核说明，最长 255 字符 | body | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.6.4 退款信息。

# 2.9优惠券相关接口

# 2.9.1查询优惠券列表

### 接口地址

GET /api/v1/coupons

### 接口描述

查询当前用户的优惠券，可按状态筛选。需要认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| state | 状态筛选，可空 | query | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

data 为数组，元素字段：

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| couponId | string | 优惠券 ID |  |
| name | string | 名称 |  |
| subtitle | string | 副标题 |  |
| kind | string | 类型 |  |
| thresholdAmount | integer | 使用门槛，分 |  |
| discountAmount | integer | 减免金额，分 |  |
| discountRate | integer | 折扣率 |  |
| maxDiscountAmount | integer | 最大优惠，分 |  |
| merchantIds | array | 适用档口 ID |  |
| state | string | 状态 |  |
| startAt | string | 生效时间 |  |
| endAt | string | 失效时间 |  |
| usedAt | string | 使用时间 |  |
| orderNo | string | 使用订单号 |  |
| source | string | 来源 |  |

# 2.10评价相关接口

# 2.10.1查询商家评价列表

### 接口地址

GET /api/v1/merchants/{merchantId}/reviews

### 接口描述

查询指定档口的评价列表。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| merchantId | 档口 ID | path | true | integer |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |

### 响应参数

data 为数组，元素字段同 2.10.4 返回。

# 2.10.2查询我的评价

### 接口地址

GET /api/v1/reviews

### 接口描述

查询当前用户发表的评价。需要认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

data 为数组，元素字段同 2.10.4 返回。

# 2.10.3查询订单评价

### 接口地址

GET /api/v1/orders/{orderNo}/review

### 接口描述

查询指定订单的评价。需要认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| orderNo | 订单号 | path | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

字段同 2.10.4 返回。

# 2.10.4发表评价

### 接口地址

POST /api/v1/reviews

### 接口描述

为已完成订单发表评价。需要认证。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "orderNo": "20261120143000000001",
  "ratingScore": 5,
  "tasteScore": 5,
  "speedScore": 4,
  "tags": ["分量足", "味道好"],
  "content": "味道不错",
  "images": [],
  "anonymous": false
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| orderNo | 订单号 | body | true | string |  |
| ratingScore | 总评分 | body | false | integer |  |
| tasteScore | 口味评分 | body | false | integer |  |
| speedScore | 速度评分 | body | false | integer |  |
| tags | 标签数组 | body | false | array |  |
| content | 评价内容 | body | false | string |  |
| images | 图片地址数组 | body | false | array |  |
| anonymous | 是否匿名 | body | false | boolean |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| reviewId | integer | 评价 ID |  |
| orderNo | string | 订单号 |  |
| merchantId | integer | 档口 ID |  |
| merchantName | string | 档口名称 |  |
| userId | integer | 用户 ID |  |
| userName | string | 用户昵称 |  |
| userAvatar | string | 用户头像 |  |
| ratingScore | integer | 总评分 |  |
| tasteScore | integer | 口味评分 |  |
| speedScore | integer | 速度评分 |  |
| tags | array | 标签 |  |
| content | string | 评价内容 |  |
| images | array | 图片地址 |  |
| anonymous | boolean | 是否匿名 |  |
| dishNames | array | 菜品名称 |  |
| replyContent | string | 商家回复 |  |
| createdAt | string | 创建时间 |  |

# 2.11通知相关接口

# 2.11.1查询通知列表

### 接口地址

GET /api/v1/notifications

### 接口描述

查询当前用户的通知消息列表。需要认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

data 为数组，元素字段：

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| messageId | integer | 消息 ID |  |
| scene | string | 场景 |  |
| title | string | 标题 |  |
| content | string | 内容 |  |
| bizType | string | 业务类型 |  |
| bizId | string | 业务 ID |  |
| readFlag | boolean | 是否已读 |  |
| createdAt | string | 创建时间 |  |

# 2.11.2标记已读

### 接口地址

PATCH /api/v1/notifications/{messageId}/read

### 接口描述

将指定通知标记为已读，返回最新通知列表。需要认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| messageId | 消息 ID | path | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

同 2.11.1 查询通知列表。

# 2.12地址相关接口

# 2.12.1查询地址列表

### 接口地址

GET /api/v1/addresses

### 接口描述

查询当前用户的收货地址列表。需要认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

data 为数组，元素字段同 2.12.2 返回。

# 2.12.2新增地址

### 接口地址

POST /api/v1/addresses

### 接口描述

新增收货地址，返回最新地址列表。需要认证。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "contactName": "张三",
  "contactPhone": "13800000001",
  "campusArea": "主校区",
  "building": "1 号楼",
  "roomNo": "301",
  "tag": "宿舍",
  "remark": "",
  "isDefault": true
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| contactName | 联系人姓名 | body | false | string |  |
| contactPhone | 联系人电话 | body | false | string |  |
| campusArea | 校区 | body | false | string |  |
| building | 楼栋 | body | false | string |  |
| roomNo | 房间号 | body | false | string |  |
| tag | 标签 | body | false | string |  |
| remark | 备注 | body | false | string |  |
| isDefault | 是否默认 | body | false | boolean |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

data 为数组，元素字段：

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| addressId | string | 地址 ID |  |
| contactName | string | 联系人姓名 |  |
| contactPhone | string | 联系人电话 |  |
| campusArea | string | 校区 |  |
| building | string | 楼栋 |  |
| roomNo | string | 房间号 |  |
| tag | string | 标签 |  |
| remark | string | 备注 |  |
| isDefault | boolean | 是否默认 |  |
| updatedAt | string | 更新时间 |  |

# 2.12.3修改地址

### 接口地址

PUT /api/v1/addresses/{addressId}

### 接口描述

修改收货地址，返回最新地址列表。需要认证。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| addressId | 地址 ID | path | true | string |  |
| contactName | 联系人姓名 | body | false | string |  |
| contactPhone | 联系人电话 | body | false | string |  |
| campusArea | 校区 | body | false | string |  |
| building | 楼栋 | body | false | string |  |
| roomNo | 房间号 | body | false | string |  |
| tag | 标签 | body | false | string |  |
| remark | 备注 | body | false | string |  |
| isDefault | 是否默认 | body | false | boolean |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

同 2.12.2 返回。

# 2.12.4删除地址

### 接口地址

DELETE /api/v1/addresses/{addressId}

### 接口描述

删除收货地址，返回最新地址列表。需要认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| addressId | 地址 ID | path | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

同 2.12.2 返回。

# 2.12.5设为默认地址

### 接口地址

PATCH /api/v1/addresses/{addressId}/default

### 接口描述

将指定地址设为默认地址，返回最新地址列表。需要认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| addressId | 地址 ID | path | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

同 2.12.2 返回。

# 2.13收藏相关接口

# 2.13.1查询收藏列表

### 接口地址

GET /api/v1/favorites

### 接口描述

查询当前用户的收藏列表。需要认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

data 为数组，元素字段：`favoriteId`、`targetType`、`targetId`、`name`、`subtitle`、`imageUrl`。

# 2.13.2添加收藏

### 接口地址

POST /api/v1/favorites

### 接口描述

添加收藏，返回最新收藏列表。需要认证。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "targetType": "MERCHANT",
  "targetId": "1"
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| targetType | 目标类型 | body | false | string |  |
| targetId | 目标 ID | body | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

同 2.13.1 查询收藏列表。

# 2.13.3取消收藏

### 接口地址

DELETE /api/v1/favorites

### 接口描述

取消收藏，返回最新收藏列表。需要认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| targetType | 目标类型 | query | false | string |  |
| targetId | 目标 ID | query | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

同 2.13.1 查询收藏列表。

# 2.14实时推送相关接口

# 2.14.1事件流

### 接口地址

GET /api/v1/events/stream

### 接口描述

建立 SSE 长连接推送订单事件。ACCOUNT 主体订阅所属商家维度，USER 主体订阅个人维度。凭证无效时返回一条 `ping` 事件后立即结束，事件内容含 `error` 字段。

### 请求数据类型

Params

### 响应数据类型

text/event-stream

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| token | accessToken，也可通过 Authorization 头传递 | query | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

事件名 `ping` 用于保活，业务事件携带订单状态变更数据。断线时前端降级为 3 秒轮询。

# 2.15系统相关接口

# 2.15.1健康检查

### 接口地址

GET /api/v1/system/health

### 接口描述

检查服务与数据库状态，无需认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| app | string | 应用名称 |  |
| dbNow | string | 数据库当前时间 |  |
| dbTimezone | string | 数据库时区 |  |
| isolation | string | 事务隔离级别 |  |
| tableCount | integer | 业务表数量 |  |
| migratedVersion | string | 已迁移版本号 |  |

# 2.15.2系统统计

### 接口地址

GET /api/v1/system/stats

### 接口描述

查询系统运行统计，无需认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |

### 响应参数

字段：`merchants`、`dishes`、`orders`、`pendingPayment`、`readyOrders`、`uptimeSeconds`。

# 2.16商家控制台相关接口

本章接口路径统一前缀 `/api/v1/merchant`，均要求 ACCOUNT 主体且具备 MERCHANT_ 角色。MERCHANT_STAFF 仅放行 GET 与订单操作 POST（accept/start/ready/verify/reject），其余写操作返回 403。档口 ID 由登录凭证确定，不从请求参数读取。

# 2.16.1查询订单列表

### 接口地址

GET /api/v1/merchant/orders

### 接口描述

查询本店订单列表，支持状态、关键字与游标筛选。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| status | 状态筛选，可空 | query | false | string |  |
| afterId | 游标，可空 | query | false | string |  |
| keyword | 关键字，可空 | query | false | string |  |
| page | 当前页码，默认 1 | query | false | integer |  |
| pageSize | 每页条数，默认 20 | query | false | integer |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

data 为分页对象：`list`、`total`、`page`、`pageSize`。列表元素字段：

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| orderNo | string | 订单号 |  |
| merchantId | string | 档口 ID |  |
| merchantName | string | 档口名称 |  |
| status | string | 订单状态 |  |
| payAmount | integer | 应付金额，分 |  |
| itemCount | integer | 商品件数 |  |
| pickupCode | string | 取餐码 |  |
| bizDate | string | 营业日期 |  |
| mealPeriod | string | 餐段 |  |
| createdAt | string | 创建时间 |  |
| expectTakeAt | string | 预计取餐时间 |  |
| pickupDeadlineAt | string | 取餐截止时间 |  |
| serverTime | string | 服务器时间 |  |
| clientOrderNo | string | 客户端幂等键 |  |
| fulfillmentType | string | 履约方式 |  |
| contactName | string | 联系人姓名 |  |
| contactPhone | string | 联系人电话 |  |
| remark | string | 备注 |  |
| version | integer | 乐观锁版本 |  |
| items | array | 菜品行列表 |  |
| timeline | array | 状态时间线 |  |
| payment | object | 支付信息 |  |
| refund | object | 退款信息 |  |
| lastOperatorName | string | 最后操作人 |  |
| waitingSeconds | integer | 已等待秒数 |  |

# 2.16.2查询看板汇总

### 接口地址

GET /api/v1/merchant/orders/summary

### 接口描述

查询看板汇总，含最新订单号与各状态订单数。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| newestOrderNo | string | 最新订单号 |  |
| counts | object | 各状态订单数 |  |
| serverTime | string | 服务器时间 |  |

# 2.16.3接单

### 接口地址

POST /api/v1/merchant/orders/{orderNo}/accept

### 接口描述

将已支付订单接单，状态从 PAID 推进到 ACCEPTED。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| orderNo | 订单号 | path | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.16.1 订单元素。

# 2.16.4开始制作

### 接口地址

POST /api/v1/merchant/orders/{orderNo}/start

### 接口描述

将已接单订单置为制作中，状态从 ACCEPTED 推进到 COOKING。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| orderNo | 订单号 | path | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.16.1 订单元素。

# 2.16.5出餐

### 接口地址

POST /api/v1/merchant/orders/{orderNo}/ready

### 接口描述

将制作中订单置为待取餐，状态从 COOKING 推进到 READY。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| orderNo | 订单号 | path | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.16.1 订单元素。

# 2.16.6核销取餐码

### 接口地址

POST /api/v1/merchant/orders/{orderNo}/verify

### 接口描述

校验取餐码并核销，状态从 READY 推进到 COMPLETED。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "pickupCode": "A012"
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| orderNo | 订单号 | path | true | string |  |
| pickupCode | 取餐码 | body | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.16.1 订单元素。

# 2.16.7拒单

### 接口地址

POST /api/v1/merchant/orders/{orderNo}/reject

### 接口描述

拒绝已支付订单，需填写原因。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "reason": "食材售罄"
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| orderNo | 订单号 | path | true | string |  |
| reason | 拒单原因 | body | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.16.1 订单元素。

# 2.16.8取餐码查询

### 接口地址

GET /api/v1/merchant/pickup

### 接口描述

按取餐码查询订单。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| pickupCode | 取餐码 | query | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.16.1 订单元素。

# 2.16.9查询库存看板

### 接口地址

GET /api/v1/merchant/stock

### 接口描述

查询本店当日菜品库存看板。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| merchantId | string | 档口 ID |  |
| bizDate | string | 营业日期 |  |
| serverTime | string | 服务器时间 |  |
| rows | array | 库存行列表 |  |

库存行字段：`dishId`、`name`、`categoryId`、`categoryName`、`status`、`stockMode`、`total`、`locked`、`sold`、`remaining`、`soldOut`、`monthSold`。

# 2.16.10修改库存

### 接口地址

PATCH /api/v1/merchant/dishes/{dishId}/stock

### 接口描述

修改指定菜品在指定营业日期的库存总量。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "bizDate": "2026-11-20",
  "stockTotal": 50
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| dishId | 菜品 ID | path | true | string |  |
| bizDate | 营业日期 | body | true | string |  |
| stockTotal | 库存总量 | body | true | integer |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段：`dishId`、`bizDate`、`total`、`locked`、`sold`、`remaining`、`soldOut`、`status`。

# 2.16.11修改营业状态

### 接口地址

PATCH /api/v1/merchant/status

### 接口描述

设置本店临时打烊或恢复营业。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "closed": true,
  "reason": "临时休息"
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| closed | 是否打烊 | body | false | boolean |  |
| reason | 原因 | body | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.16.12 档口资料。

# 2.16.12查询档口资料

### 接口地址

GET /api/v1/merchant/profile

### 接口描述

查询本店资料。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| merchantId | string | 档口 ID |  |
| name | string | 档口名称 |  |
| categoryCode | string | 品类编码 |  |
| categoryName | string | 品类名称 |  |
| logoUrl | string | 标志图地址 |  |
| description | string | 简介 |  |
| contactName | string | 联系人 |  |
| contactPhone | string | 联系电话 |  |
| licenseNo | string | 许可证号 |  |
| settleAccount | string | 结算账户 |  |
| pickupPointId | string | 自提点 ID |  |
| pickupPointName | string | 自提点名称 |  |
| campusArea | string | 校区 |  |
| minOrderAmount | integer | 起送金额，分 |  |
| packFee | integer | 打包费，分 |  |
| avgPrepareMinutes | integer | 平均出餐分钟数 |  |
| monthSold | integer | 月销量 |  |
| ratingScore | integer | 评分 |  |
| ratingCount | integer | 评价数 |  |
| status | integer | 档口状态 |  |
| open | boolean | 是否营业 |  |
| closeReason | string | 打烊原因 |  |
| openTime | string | 营业开始时间 |  |
| closeTime | string | 营业结束时间 |  |
| serverTime | string | 服务器时间 |  |

# 2.16.13修改档口资料

### 接口地址

PATCH /api/v1/merchant/profile

### 接口描述

修改本店资料，返回更新后的档口资料。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "name": "川香盖浇饭",
  "description": "川味盖浇饭",
  "minOrderAmount": 1000,
  "packFee": 100
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| name | 档口名称 | body | false | string |  |
| categoryCode | 品类编码 | body | false | string |  |
| description | 简介 | body | false | string |  |
| logoUrl | 标志图地址 | body | false | string |  |
| contactName | 联系人 | body | false | string |  |
| contactPhone | 联系电话 | body | false | string |  |
| licenseNo | 许可证号 | body | false | string |  |
| settleAccount | 结算账户 | body | false | string |  |
| minOrderAmount | 起送金额，分 | body | false | integer |  |
| packFee | 打包费，分 | body | false | integer |  |
| avgPrepareMinutes | 平均出餐分钟数 | body | false | integer |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.16.12 档口资料。

# 2.16.14查询菜单

### 接口地址

GET /api/v1/merchant/menu

### 接口描述

查询本店后台菜单，含全部分类与菜品。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| merchant | object | 档口资料，见 2.16.12 |  |
| bizDate | string | 营业日期 |  |
| categories | array | 分类列表 |  |
| serverTime | string | 服务器时间 |  |
| stockVersion | string | 库存版本号 |  |

分类行字段：`categoryId`、`name`、`sortWeight`、`visible`、`dishes`。菜品行字段：`dishId`、`merchantId`、`categoryId`、`name`、`subtitle`、`imageUrl`、`basePrice`、`packFee`、`minBuyCount`、`maxBuyPerOrder`、`stockMode`、`monthSold`、`tags`、`recommended`、`status`、`groups`、`stock`。

# 2.16.15新增分类

### 接口地址

POST /api/v1/merchant/categories

### 接口描述

新增菜单分类，返回最新菜单。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "name": "热销",
  "sortWeight": 10,
  "visible": true
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| name | 分类名称 | body | false | string |  |
| sortWeight | 排序权重 | body | false | integer |  |
| visible | 是否可见 | body | false | boolean |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.16.14 查询菜单。

# 2.16.16分类排序

### 接口地址

PUT /api/v1/merchant/categories/sort

### 接口描述

按给定顺序重排分类，返回最新菜单。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "categoryIds": ["3", "1", "2"]
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| categoryIds | 分类 ID 有序数组 | body | true | array |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.16.14 查询菜单。

# 2.16.17修改分类

### 接口地址

PUT /api/v1/merchant/categories/{categoryId}

### 接口描述

修改指定分类，返回最新菜单。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| categoryId | 分类 ID | path | true | string |  |
| name | 分类名称 | body | false | string |  |
| sortWeight | 排序权重 | body | false | integer |  |
| visible | 是否可见 | body | false | boolean |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.16.14 查询菜单。

# 2.16.18删除分类

### 接口地址

DELETE /api/v1/merchant/categories/{categoryId}

### 接口描述

删除指定分类，分类下存在菜品时返回 30041。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| categoryId | 分类 ID | path | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.16.14 查询菜单。

# 2.16.19新增菜品

### 接口地址

POST /api/v1/merchant/dishes

### 接口描述

新增菜品，返回新菜品 ID。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "categoryId": "1",
  "name": "宫保鸡丁盖饭",
  "basePrice": 1500,
  "packFee": 100,
  "status": 1,
  "groups": []
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| categoryId | 分类 ID | body | false | string |  |
| name | 菜品名称 | body | false | string |  |
| subtitle | 副标题 | body | false | string |  |
| imageUrl | 图片地址 | body | false | string |  |
| description | 描述 | body | false | string |  |
| basePrice | 基础价，分 | body | false | integer |  |
| packFee | 打包费，分 | body | false | integer |  |
| minBuyCount | 起购数量 | body | false | integer |  |
| maxBuyPerOrder | 单单限购 | body | false | integer |  |
| stockMode | 库存模式 | body | false | integer |  |
| stockTotal | 库存总量 | body | false | integer |  |
| saleTimeStart | 售卖开始时间 | body | false | string |  |
| saleTimeEnd | 售卖结束时间 | body | false | string |  |
| tags | 标签数组 | body | false | array |  |
| recommended | 是否推荐 | body | false | boolean |  |
| status | 状态 | body | false | integer |  |
| sortWeight | 排序权重 | body | false | integer |  |
| groups | 规格组数组 | body | false | array |  |

规格组字段：`groupId`、`name`、`groupType`、`required`、`minSelect`、`maxSelect`、`sortWeight`、`options`；规格项字段：`optionId`、`name`、`priceDelta`、`isDefault`、`stock`、`sortWeight`、`status`。

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| dishId | string | 新菜品 ID |  |

# 2.16.20批量上下架

### 接口地址

PUT /api/v1/merchant/dishes/status

### 接口描述

批量修改菜品状态，返回最新菜单。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "dishIds": ["11", "12"],
  "status": 1
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| dishIds | 菜品 ID 数组 | body | true | array |  |
| status | 目标状态 | body | true | integer |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.16.14 查询菜单。

# 2.16.21修改菜品

### 接口地址

PUT /api/v1/merchant/dishes/{dishId}

### 接口描述

修改指定菜品，返回新菜品 ID。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| dishId | 菜品 ID | path | true | string |  |

其余请求字段同 2.16.19 新增菜品。

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| dishId | string | 菜品 ID |  |

# 2.16.22删除菜品

### 接口地址

DELETE /api/v1/merchant/dishes/{dishId}

### 接口描述

删除指定菜品，返回最新菜单。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| dishId | 菜品 ID | path | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.16.14 查询菜单。

# 2.16.23查询营业时间

### 接口地址

GET /api/v1/merchant/hours

### 接口描述

查询本店每周营业时间与特殊营业日。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| merchantId | string | 档口 ID |  |
| rows | array | 每周营业时间 |  |
| overrides | array | 特殊营业日 |  |
| serverTime | string | 服务器时间 |  |

营业时间行字段：`dayOfWeek`、`dayLabel`、`openTime`、`closeTime`、`acceptStart`、`acceptEnd`、`closed`。特殊营业日字段：`bizDate`、`overrideType`、`openTime`、`closeTime`、`reason`。

# 2.16.24保存营业时间

### 接口地址

PUT /api/v1/merchant/hours

### 接口描述

保存每周营业时间，返回最新设置。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "rows": [
    { "dayOfWeek": 1, "openTime": "10:00", "closeTime": "20:00", "acceptStart": "09:30", "acceptEnd": "19:30", "closed": false }
  ]
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| rows | 营业时间数组 | body | true | array |  |
| rows[].dayOfWeek | 星期，1 至 7 | body | true | integer |  |
| rows[].openTime | 营业开始时间 | body | false | string |  |
| rows[].closeTime | 营业结束时间 | body | false | string |  |
| rows[].acceptStart | 接单开始时间 | body | false | string |  |
| rows[].acceptEnd | 接单结束时间 | body | false | string |  |
| rows[].closed | 是否休息 | body | false | boolean |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.16.23 查询营业时间。

# 2.16.25保存特殊营业日

### 接口地址

PUT /api/v1/merchant/hours/override

### 接口描述

新增或更新指定日期的特殊营业安排。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "bizDate": "2026-11-25",
  "overrideType": "CLOSED",
  "reason": "店铺调整"
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| bizDate | 营业日期 | body | true | string |  |
| overrideType | 安排类型 | body | false | string |  |
| openTime | 营业开始时间 | body | false | string |  |
| closeTime | 营业结束时间 | body | false | string |  |
| reason | 原因 | body | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.16.23 查询营业时间。

# 2.16.26删除特殊营业日

### 接口地址

DELETE /api/v1/merchant/hours/override

### 接口描述

删除指定日期的特殊营业安排。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| bizDate | 营业日期 | query | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.16.23 查询营业时间。

# 2.16.27每日报表

### 接口地址

GET /api/v1/merchant/stats/daily

### 接口描述

查询本店最近 N 天每日经营数据，按日期升序补零返回。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| days | 天数，默认 14 | query | false | integer |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

data 为数组，元素字段：`bizDate`、`orderCount`、`paidCount`、`cancelCount`、`turnoverAmount`、`netAmount`、`avgPrepareSeconds`。流水为已支付且非 CLOSED/CANCELLED 的金额，净额为流水减已退款，金额单位分。

# 2.16.28菜品报表

### 接口地址

GET /api/v1/merchant/stats/dishes

### 接口描述

查询本店指定营业日期的菜品销售报表。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| bizDate | 营业日期，缺省时取当天 | query | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

data 为数组，元素字段：`dishId`、`dishName`、`bizDate`、`soldCount`、`amountSum`、`stockTotal`、`soldOut`。

# 2.16.29经营概览

### 接口地址

GET /api/v1/merchant/stats/overview

### 接口描述

查询本店当日经营概览，实时从订单表聚合。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段：`bizDate`、`serverTime`、`waitingCount`、`cookingCount`、`readyCount`、`refundCount`、`orderCount`、`paidCount`、`cancelCount`、`turnoverAmount`、`netAmount`、`avgPrepareSeconds`、`soldOutDishes`。

# 2.17平台管理相关接口

本章接口路径统一前缀 `/api/v1/platform`，仅 PLATFORM_ADMIN 可调用，其余主体返回 403。

# 2.17.1平台概览

### 接口地址

GET /api/v1/platform/overview

### 接口描述

查询平台经营概览与每日趋势。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| serverTime | string | 服务器时间 |  |
| merchantTotal | integer | 档口总数 |  |
| merchantOpen | integer | 营业档口数 |  |
| merchantPending | integer | 待审核档口数 |  |
| dishTotal | integer | 菜品总数 |  |
| orderToday | integer | 今日订单数 |  |
| paidToday | integer | 今日支付数 |  |
| cancelToday | integer | 今日取消数 |  |
| turnoverToday | integer | 今日流水，分 |  |
| netToday | integer | 今日净额，分 |  |
| readyOrders | integer | 待取餐订单数 |  |
| unpaidOrders | integer | 待支付订单数 |  |
| dailyTrend | array | 每日趋势，元素同 2.16.27 |  |
| campusAreas | array | 校区列表 |  |

# 2.17.2查询档口审核列表

### 接口地址

GET /api/v1/platform/merchants

### 接口描述

按状态查询档口审核列表。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| status | 档口状态，可空 | query | false | integer |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

data 为数组，元素字段：`merchantId`、`name`、`categoryCode`、`categoryName`、`contactName`、`contactPhone`、`licenseNo`、`pickupPointName`、`campusArea`、`status`、`rejectReason`、`monthSold`、`ratingScore`、`open`、`closeReason`、`openTime`、`closeTime`、`avgPrepareMinutes`。

# 2.17.3审核通过

### 接口地址

POST /api/v1/platform/merchants/{merchantId}/approve

### 接口描述

通过档口入驻审核。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| merchantId | 档口 ID | path | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.17.2 元素。

# 2.17.4审核拒绝

### 接口地址

POST /api/v1/platform/merchants/{merchantId}/reject

### 接口描述

拒绝档口入驻审核，需填写原因。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "reason": "资料不完整"
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| merchantId | 档口 ID | path | true | string |  |
| reason | 拒绝原因 | body | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.17.2 元素。

# 2.17.5修改档口状态

### 接口地址

PATCH /api/v1/platform/merchants/{merchantId}/status

### 接口描述

修改档口状态，可携带期望状态做并发校验。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "status": 2,
  "expectedStatus": 1
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| merchantId | 档口 ID | path | true | string |  |
| status | 目标状态 | body | true | integer |  |
| expectedStatus | 期望当前状态，可空 | body | false | integer |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.17.2 元素。

# 2.17.6查询配置项

### 接口地址

GET /api/v1/platform/configs

### 接口描述

查询平台配置项列表。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

data 为数组，元素字段：`configKey`、`configValue`、`valueType`、`name`、`description`、`isPublic`。

# 2.17.7保存配置项

### 接口地址

PATCH /api/v1/platform/configs/{configKey}

### 接口描述

修改指定配置项的值。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "configValue": "10"
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| configKey | 配置键 | path | true | string |  |
| configValue | 配置值 | body | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.17.6 元素。

# 2.18平台用户管理相关接口

本章接口路径统一前缀 `/api/v1/platform/users`，仅 PLATFORM_ADMIN 可调用。

# 2.18.1查询用户列表

### 接口地址

GET /api/v1/platform/users

### 接口描述

分页查询平台用户，支持关键字、状态与审核状态筛选。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| page | 当前页码，默认 1 | query | false | string |  |
| pageSize | 每页条数，默认 20，最大 100 | query | false | string |  |
| keyword | 关键字，可空 | query | false | string |  |
| status | 账号状态，可空 | query | false | string |  |
| verifyStatus | 审核状态，可空 | query | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

data 为分页对象：`list`、`total`、`page`、`pageSize`。用户行字段：

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| userId | string | 用户 ID |  |
| nickname | string | 昵称 |  |
| realName | string | 真实姓名 |  |
| phone | string | 手机号 |  |
| studentNo | string | 学号 |  |
| college | string | 学院 |  |
| campusArea | string | 校区 |  |
| dormBuilding | string | 宿舍楼 |  |
| verifyStatus | integer | 审核状态 |  |
| creditScore | integer | 信用分 |  |
| orderCount | integer | 订单数 |  |
| status | integer | 账号状态 |  |
| forbiddenReason | string | 封禁原因 |  |
| verifyNote | string | 审核说明 |  |
| createdAt | string | 创建时间 |  |
| lastLoginAt | string | 最后登录时间 |  |

# 2.18.2查询用户汇总

### 接口地址

GET /api/v1/platform/users/summary

### 接口描述

查询用户总数、正常数、封禁数与待审核数。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段：`total`、`normal`、`banned`、`pendingVerification`。

# 2.18.3修改用户状态

### 接口地址

PATCH /api/v1/platform/users/{userId}/status

### 接口描述

封禁或解封用户，需携带期望状态做并发校验。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "status": 0,
  "reason": "违规",
  "expectedStatus": 1
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| userId | 用户 ID | path | true | string |  |
| status | 目标状态，0 或 1 | body | true | integer |  |
| reason | 原因，最长 255 字符 | body | false | string |  |
| expectedStatus | 期望当前状态，0 或 1 | body | true | integer |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.18.1 用户行。

# 2.18.4学籍审核

### 接口地址

PATCH /api/v1/platform/users/{userId}/verify

### 接口描述

审核用户学籍状态，需携带期望状态做并发校验。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "verifyStatus": 2,
  "expectedVerifyStatus": 1,
  "reason": ""
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| userId | 用户 ID | path | true | string |  |
| verifyStatus | 目标审核状态，0 至 3 | body | true | integer |  |
| expectedVerifyStatus | 期望当前审核状态，0 至 3 | body | true | integer |  |
| reason | 说明，最长 255 字符 | body | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.18.1 用户行。

# 2.18.5修改用户资料

### 接口地址

PATCH /api/v1/platform/users/{userId}/profile

### 接口描述

修改用户基础资料。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "nickname": "新昵称",
  "college": "计算机学院"
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| userId | 用户 ID | path | true | string |  |
| nickname | 昵称，最长 64 字符 | body | false | string |  |
| realName | 真实姓名，最长 32 字符 | body | false | string |  |
| studentNo | 学号，最长 32 字符 | body | false | string |  |
| phone | 手机号，最长 20 字符 | body | false | string |  |
| college | 学院，最长 64 字符 | body | false | string |  |
| campusArea | 校区，最长 64 字符 | body | false | string |  |
| dormBuilding | 宿舍楼，最长 64 字符 | body | false | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.18.1 用户行。

# 2.18.6删除用户

### 接口地址

DELETE /api/v1/platform/users/{userId}

### 接口描述

删除用户，存在订单记录时返回 60003。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| userId | 用户 ID | path | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| userId | string | 已删除用户 ID |  |

# 2.19配送员相关接口

# 2.19.1配送员登录

### 接口地址

POST /api/v1/auth/rider/login

### 接口描述

配送员使用学号与密码登录，需具备已启用的配送资格。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "studentNo": "2022010101",
  "password": "010101"
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| studentNo | 学号 | body | true | string |  |
| password | 密码 | body | true | string |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |

### 响应参数

返回会话令牌，字段同 2.2.1 控制台登录，账号信息替换为配送员资料。

# 2.19.2查询配送员资料

### 接口地址

GET /api/v1/rider/profile

### 接口描述

查询当前配送员资料与今日统计。需要配送员认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| 暂无 |  |  |  |  |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| riderId | string | 配送员 ID |  |
| realName | string | 姓名 |  |
| phone | string | 手机号 |  |
| campusArea | string | 校区 |  |
| dormBuilding | string | 宿舍楼 |  |
| status | integer | 接单状态 |  |
| deliveryCount | integer | 累计配送单量 |  |
| incomeAmount | integer | 累计收入，分 |  |
| ratingScore | integer | 评分 |  |
| todayCount | integer | 今日配送单量 |  |
| todayIncome | integer | 今日收入，分 |  |

# 2.19.3修改接单状态

### 接口地址

PATCH /api/v1/rider/status

### 接口描述

修改配送员接单状态。需要配送员认证。

### 请求数据类型

application/json

### 响应数据类型

*/*

### 请求示例

```
{
  "status": 1
}
```

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| status | 接单状态 | body | true | integer |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.19.2 查询配送员资料。

# 2.19.4查询配送任务

### 接口地址

GET /api/v1/rider/tasks

### 接口描述

查询配送任务列表，按任务范围筛选。需要配送员认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| scope | 任务范围，POOL 或 MY 等，默认 POOL | query | false | string |  |
| page | 当前页码，默认 1 | query | false | integer |  |
| pageSize | 每页条数，默认 20 | query | false | integer |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

data 为分页对象：`list`、`total`、`page`、`pageSize`。任务字段：

| 参数名称 | 类型 | 参数说明 | schema |
| --- | --- | --- | --- |
| deliveryId | string | 配送单 ID |  |
| orderNo | string | 订单号 |  |
| status | string | 任务状态 |  |
| merchantName | string | 档口名称 |  |
| pickupPointName | string | 自提点名称 |  |
| targetBuilding | string | 目标楼栋 |  |
| dormAddress | string | 宿舍地址 |  |
| contactName | string | 联系人 |  |
| contactPhone | string | 联系电话 |  |
| pickupCode | string | 取餐码 |  |
| remark | string | 备注 |  |
| itemCount | integer | 商品件数 |  |
| riderIncome | integer | 配送收入，分 |  |
| createdAt | string | 创建时间 |  |
| acceptedAt | string | 接单时间 |  |
| pickedAt | string | 取餐时间 |  |
| deliveredAt | string | 送达时间 |  |
| items | array | 菜品明细 |  |

菜品明细字段：`dishName`、`optionText`、`quantity`。

# 2.19.5接任务

### 接口地址

POST /api/v1/rider/tasks/{id}/accept

### 接口描述

从任务池接取配送任务。需要配送员认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| id | 配送单 ID | path | true | integer |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.19.4 任务。

# 2.19.6取餐

### 接口地址

POST /api/v1/rider/tasks/{id}/pick

### 接口描述

将配送任务置为已取餐。需要配送员认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| id | 配送单 ID | path | true | integer |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.19.4 任务。

# 2.19.7送达

### 接口地址

POST /api/v1/rider/tasks/{id}/deliver

### 接口描述

将配送任务置为已送达。需要配送员认证。

### 请求数据类型

Params

### 响应数据类型

*/*

### 请求参数

| 参数名称 | 参数说明 | 请求类型 | 必须 | 数据类型 | schema |
| --- | --- | --- | --- | --- | --- |
| id | 配送单 ID | path | true | integer |  |

### 响应状态

| 状态码 | 说明 | schema |
| --- | --- | --- |
| 200 | OK |  |
| 401 | Unauthorized |  |
| 403 | Forbidden |  |

### 响应参数

字段同 2.19.4 任务。
