# MySQL Whitelist Velocity

**简体中文** | [English](./README_EN.md)

这是一个使用 MySQL 作为后端存储的 Velocity 插件，用于管理 Minecraft 白名单。

## 安装

1. 从 [Releases](https://github.com/moesnow/MySQLWhitelistVelocity/releases) 页面下载最新版本。
2. 将 JAR 文件放置在 Velocity 代理的 `plugins` 目录中。
3. 启动代理。

## 配置

插件使用位于 `plugins/mysqlwhitelistvelocity` 目录中的 `config.properties` 文件进行配置。

### 默认配置

```properties
# 白名单状态
enabled: false

# MySQL 设置
host: localhost
user: root
password: example
database: minecraft
port: 3306
table: mysql_whitelist

# 踢出消息
message: Sorry, you are not in the whitelist.
```

### 配置选项

- `enabled`: 设置为 `true` 以启用白名单。
- `host`, `user`, `password`, `database`, `port`, `table`: MySQL 数据库连接详情。
- `message`: 显示给不在白名单中的玩家的踢出消息。

## 命令

- `/mywl add <player>`: 将玩家添加到白名单。
- `/mywl del <player>`: 从白名单中移除玩家。

## 权限

- `mysqlwhitelist`: 使用白名单管理命令所需的权限。

## 使用方法

1. 在 `config.properties` 文件中配置 MySQL 连接详情。
2. 启动代理。
3. 使用 `/mywl` 命令管理白名单。

## 问题和贡献

如果遇到任何问题或有改进建议，请在 [GitHub 仓库](https://github.com/moesnow/MySQLWhitelistVelocity)上提出问题或提交拉取请求。

## 许可

此插件采用 GPL-3.0 许可证授权 - 有关详细信息，请参阅 [LICENSE](LICENSE) 文件。