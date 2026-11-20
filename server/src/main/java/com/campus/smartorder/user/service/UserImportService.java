package com.campus.smartorder.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.auth.service.PasswordHasher;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.user.api.PlatformUserDto;
import com.campus.smartorder.user.api.UserImportDto;
import com.campus.smartorder.user.domain.User;
import com.campus.smartorder.user.mapper.UserMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class UserImportService {

    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ROWS = 500;
    private static final String[] HEADERS = {"学号", "姓名", "手机号", "学院", "校区", "宿舍楼", "昵称", "初始密码"};
    private static final int[] LENGTHS = {32, 32, 20, 64, 64, 64, 64};

    private final UserMapper users;
    private final PlatformUserService profiles;
    private final PasswordHasher passwords;
    private final TransactionTemplate transaction;

    public UserImportService(UserMapper users, PlatformUserService profiles, PasswordHasher passwords,
                             PlatformTransactionManager transactionManager) {
        this.users = users;
        this.profiles = profiles;
        this.passwords = passwords;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public UserImportDto.Template template() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("用户导入");
            CellStyle text = workbook.createCellStyle();
            text.setDataFormat(workbook.createDataFormat().getFormat("@"));
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.setDefaultColumnStyle(i, text);
                sheet.setColumnWidth(i, 24 * 256);
                Cell cell = header.createCell(i, CellType.STRING);
                cell.setCellStyle(text);
                cell.setCellValue(HEADERS[i]);
            }
            Sheet guide = workbook.createSheet("填写说明");
            String[] notes = {
                    "请在第一张表填写，保留单行表头及列顺序；最多500条数据，文件不超过2MB。",
                    "所有列均为文本格式，请保留学号、手机号的前导零，禁止公式及错误单元格。",
                    "学号必填，为4至32位字母或数字；同一文件学号不区分大小写且不可重复，手机号不可重复。",
                    "新学号会创建账号，姓名和初始密码必填；初始密码6至128位，首尾空格会保留。",
                    "已有学号只更新非空资料；初始密码必须留空，不修改密码、封禁及认证状态。",
                    "手机号选填，填写时须为大陆11位手机号；姓名最多32字，学院、校区、宿舍楼和昵称最多64字。",
                    "新账号默认待认证、正常状态、信用分100、订单数0；昵称留空默认同学加学号后4位。",
                    "请先预览再提交；任一行错误均不会写入，提交时会重新校验。"
            };
            for (int i = 0; i < notes.length; i++) {
                guide.createRow(i).createCell(0).setCellValue(notes[i]);
            }
            workbook.write(out);
            return new UserImportDto.Template("用户导入模板.xlsx", Base64.getEncoder().encodeToString(out.toByteArray()));
        } catch (IOException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "模板生成失败");
        }
    }

    public UserImportDto.Result preview(UserImportDto.FileRequest file) {
        List<ImportRow> rows = parse(file);
        validate(rows);
        return result(rows, false);
    }

    public UserImportDto.Result commit(UserImportDto.FileRequest file) {
        List<ImportRow> rows = parse(file);
        ImportRow[] active = new ImportRow[1];
        try {
            return transaction.execute(status -> {
                validate(rows);
                if (rows.stream().anyMatch(row -> !row.errors.isEmpty())) {
                    return result(rows, false);
                }
                for (ImportRow row : rows) {
                    active[0] = row;
                    if (row.existingId == null) {
                        create(row);
                    } else {
                        String[] v = row.values;
                        profiles.updateProfile(row.existingId, new PlatformUserDto.ProfileUpdateRequest(
                                nonempty(v[6]), nonempty(v[1]), null, nonempty(v[2]),
                                nonempty(v[3]), nonempty(v[4]), nonempty(v[5])));
                    }
                }
                return result(rows, true);
            });
        } catch (DataIntegrityViolationException ex) {
            return conflict(rows, active[0]);
        } catch (BizException ex) {
            if (active[0] == null) {
                throw ex;
            }
            return conflict(rows, active[0]);
        }
    }

    private UserImportDto.Result conflict(List<ImportRow> rows, ImportRow active) {
        if (active == null) {
            throw invalid("用户数据已变更，请重新预览后提交");
        }
        active.errors.add("用户数据或学号、手机号占用情况已变更，本次导入全部回滚，请重新预览");
        return result(rows, false);
    }

    private void create(ImportRow row) {
        String[] v = row.values;
        User user = new User();
        user.setStudentNo(v[0]);
        user.setRealName(v[1]);
        user.setPhone(nonempty(v[2]));
        user.setCollege(v[3]);
        user.setCampusArea(v[4]);
        user.setDormBuilding(v[5]);
        user.setNickname(v[6].isEmpty() ? "同学" + v[0].substring(v[0].length() - 4) : v[6]);
        user.setPasswordHash(passwords.encode(v[7]));
        user.setAuthSource(4);
        user.setVerifyStatus(User.VERIFY_PENDING);
        user.setCreditScore(100);
        user.setOrderCount(0L);
        user.setStatus(User.STATUS_NORMAL);
        users.insert(user);
    }

    private void validate(List<ImportRow> rows) {
        Map<String, List<ImportRow>> studentNos = new HashMap<>();
        Map<String, List<ImportRow>> phones = new HashMap<>();
        for (ImportRow row : rows) {
            String[] v = row.values;
            if (!v[0].matches("[A-Za-z0-9]{4,32}")) {
                row.errors.add("学号必填且须为4至32位字母或数字");
            } else {
                studentNos.computeIfAbsent(v[0].toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(row);
                User existing = users.selectOne(new LambdaQueryWrapper<User>()
                        .select(User::getId).eq(User::getStudentNo, v[0]));
                if (existing != null) {
                    row.existingId = existing.getId();
                }
            }
            for (int i = 0; i < LENGTHS.length; i++) {
                if (v[i].codePointCount(0, v[i].length()) > LENGTHS[i]) {
                    row.errors.add(HEADERS[i] + "不能超过" + LENGTHS[i] + "字");
                }
            }
            if (!v[2].isEmpty()) {
                phones.computeIfAbsent(v[2], key -> new ArrayList<>()).add(row);
                if (!v[2].matches("1[3-9][0-9]{9}")) {
                    row.errors.add("手机号须为大陆11位手机号");
                } else {
                    User owner = users.selectOne(new LambdaQueryWrapper<User>()
                            .select(User::getId).eq(User::getPhone, v[2]));
                    if (owner != null && !Objects.equals(owner.getId(), row.existingId)) {
                        row.errors.add("手机号已被其他用户使用");
                    }
                }
            }
            if (row.existingId == null) {
                if (v[1].isEmpty()) {
                    row.errors.add("新增用户姓名必填");
                }
                if (v[7].isBlank() || v[7].length() < 6 || v[7].length() > 128) {
                    row.errors.add("新增用户初始密码须为6至128位");
                }
            } else if (!v[7].isEmpty()) {
                row.errors.add("已有用户初始密码必须留空，导入不会修改密码");
            }
        }
        duplicates(studentNos, "文件内学号重复");
        duplicates(phones, "文件内手机号重复");
    }

    private void duplicates(Map<String, List<ImportRow>> groups, String message) {
        groups.values().stream().filter(group -> group.size() > 1)
                .forEach(group -> group.forEach(row -> row.errors.add(message)));
    }

    private List<ImportRow> parse(UserImportDto.FileRequest file) {
        if (file == null || file.fileName() == null
                || !file.fileName().toLowerCase(Locale.ROOT).endsWith(".xlsx")
                || file.contentBase64() == null || file.contentBase64().isEmpty()) {
            throw invalid("请上传xlsx文件");
        }
        if (file.contentBase64().length() > 4 * ((MAX_BYTES + 2) / 3)) {
            throw invalid("文件不能超过2MB");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(file.contentBase64());
        } catch (IllegalArgumentException ex) {
            throw invalid("文件Base64内容无效");
        }
        if (bytes.length > MAX_BYTES) {
            throw invalid("文件不能超过2MB");
        }
        checkZip(bytes);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (workbook.getWorkbookType() != XSSFWorkbookType.XLSX || workbook.getNumberOfSheets() == 0) {
                throw invalid("文件必须为xlsx工作簿");
            }
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getNumMergedRegions() != 0) {
                throw invalid("导入表不能包含合并单元格");
            }
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            Row header = sheet.getRow(0);
            if (header == null) {
                throw invalid("缺少表头");
            }
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.getCell(i);
                if (cell == null || cell.getCellType() != CellType.STRING
                        || !HEADERS[i].equals(cell.getStringCellValue().strip())) {
                    throw invalid("表头须按模板顺序填写且不得重复");
                }
            }
            for (Cell cell : header) {
                if (cell.getColumnIndex() >= HEADERS.length && !blank(cell)) {
                    throw invalid("表头存在多余非空列");
                }
            }
            List<ImportRow> rows = new ArrayList<>();
            for (Row source : sheet) {
                if (source.getRowNum() == 0) {
                    continue;
                }
                boolean empty = true;
                for (Cell cell : source) {
                    empty &= blank(cell);
                }
                if (empty) {
                    continue;
                }
                if (rows.size() >= MAX_ROWS) {
                    throw invalid("数据不能超过500行");
                }
                ImportRow row = new ImportRow(source.getRowNum() + 1);
                for (Cell cell : source) {
                    if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR) {
                        row.errors.add("不允许公式或错误单元格");
                    }
                    if (cell.getColumnIndex() >= HEADERS.length && !blank(cell)) {
                        row.errors.add("存在多余非空列");
                    }
                }
                for (int i = 0; i < HEADERS.length; i++) {
                    Cell cell = source.getCell(i);
                    String value = "";
                    if (cell != null && cell.getCellType() == CellType.STRING) {
                        value = cell.getStringCellValue();
                    } else if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                        value = formatter.formatCellValue(cell);
                    } else if (cell != null && cell.getCellType() == CellType.BOOLEAN) {
                        row.errors.add(HEADERS[i] + "须为文本");
                    }
                    row.values[i] = i == 7 ? value : value.strip();
                }
                rows.add(row);
            }
            return rows;
        } catch (BizException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw invalid("无法读取xlsx文件，请检查文件格式或重新下载模板");
        }
    }

    private void checkZip(byte[] bytes) {
        int count = 0;
        long total = 0;
        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > 128 || !names.add(entry.getName())) {
                    throw invalid("工作簿压缩结构无效或超出安全限制");
                }
                long size = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    size += read;
                    total += read;
                    if (total > 16 * 1024 * 1024) {
                        throw invalid("工作簿解压大小超出安全限制");
                    }
                }
                if (size > 1024 * 1024 && size > Math.max(1, entry.getCompressedSize()) * 100) {
                    throw invalid("工作簿压缩比例超出安全限制");
                }
            }
            if (!names.contains("[Content_Types].xml") || !names.contains("xl/workbook.xml")) {
                throw invalid("文件必须为xlsx工作簿");
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw invalid("工作簿压缩结构损坏");
        }
    }

    private boolean blank(Cell cell) {
        return cell.getCellType() == CellType.BLANK
                || (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank());
    }

    private String nonempty(String value) {
        return value.isEmpty() ? null : value;
    }

    private BizException invalid(String message) {
        return new BizException(ErrorCode.INVALID_PARAM, message);
    }

    private UserImportDto.Result result(List<ImportRow> rows, boolean committed) {
        List<UserImportDto.RowResult> results = rows.stream().map(row -> {
            String[] v = row.values;
            return new UserImportDto.RowResult(row.number, v[0], v[1], v[2], v[3], v[4], v[5], v[6],
                    row.existingId == null ? "CREATE" : "UPDATE", List.copyOf(row.errors));
        }).toList();
        int creates = (int) rows.stream().filter(row -> row.existingId == null).count();
        int errors = (int) rows.stream().filter(row -> !row.errors.isEmpty()).count();
        return new UserImportDto.Result(results, rows.size(), creates, rows.size() - creates, errors, committed);
    }

    private static final class ImportRow {
        private final int number;
        private final String[] values = new String[8];
        private final List<String> errors = new ArrayList<>();
        private Long existingId;

        private ImportRow(int number) {
            this.number = number;
        }
    }
}
