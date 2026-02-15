package com.example.farm_management_system.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class PermissionService {

    public enum Permission {
        OWNER, PUBLIC_READ, PUBLIC_WRITE, SHARED_READ, SHARED_WRITE, DENY
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Permission check(Integer userId, long fileId) {

        Map<String,Object> f = jdbcTemplate.queryForMap(
                "SELECT owner_id, is_public, can_write FROM user_files WHERE file_id=?",
                fileId);

        int owner = (int) f.get("owner_id");

        // 修复：处理 TINYINT(1) 可能返回 Boolean 或 Number
        int pub = toInt(f.get("is_public"));
        int pubWrite = toInt(f.get("can_write"));

        if(userId != null && owner == userId)
            return Permission.OWNER;

        if(pub == 1)
            return pubWrite==1?Permission.PUBLIC_WRITE:Permission.PUBLIC_READ;

        if(userId == null)
            return Permission.DENY;

        var list = jdbcTemplate.queryForList(
                "SELECT can_read, can_write FROM file_permissions WHERE file_id=? AND user_id=?",
                fileId,userId);

        if(list.isEmpty()) return Permission.DENY;

        Map<String,Object> p=list.get(0);

        // 修复：处理 TINYINT(1) 可能返回 Boolean 或 Number
        int w = toInt(p.get("can_write"));
        int r = toInt(p.get("can_read"));

        if(w==1) return Permission.SHARED_WRITE;
        if(r==1) return Permission.SHARED_READ;

        return Permission.DENY;
    }

    /**
     * 安全地将 Object 转换为 int（处理 Boolean 和 Number 类型）
     * MySQL 的 TINYINT(1) 在 JDBC 中可能被映射为 Boolean 或 Number
     */
    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1 : 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
}