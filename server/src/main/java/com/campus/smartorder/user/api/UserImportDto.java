package com.campus.smartorder.user.api;

import java.util.List;

public final class UserImportDto {

    private UserImportDto() {
    }

    public record FileRequest(String fileName, String contentBase64) {
        @Override
        public String toString() {
            return "UserImportFile[redacted]";
        }
    }

    public record Template(String fileName, String contentBase64) {
    }

    public record RowResult(int rowNumber, String studentNo, String realName, String phone,
                            String college, String campusArea, String dormBuilding, String nickname,
                            String action, List<String> errors) {
    }

    public record Result(List<RowResult> rows, int total, int createCount, int updateCount,
                         int errorCount, boolean committed) {
    }
}
