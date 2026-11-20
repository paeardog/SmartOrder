package com.campus.smartorder.user.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.smartorder.auth.service.PasswordHasher;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.user.api.PlatformUserDto;
import com.campus.smartorder.user.api.UserImportDto;
import com.campus.smartorder.user.domain.User;
import com.campus.smartorder.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserImportServiceTest {

    private UserMapper users;
    private PlatformUserService profiles;
    private PasswordHasher passwords;
    private PlatformTransactionManager transactions;
    private SimpleTransactionStatus status;
    private UserImportService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), User.class);
        users = mock(UserMapper.class);
        profiles = mock(PlatformUserService.class);
        passwords = mock(PasswordHasher.class);
        transactions = mock(PlatformTransactionManager.class);
        status = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(status);
        when(passwords.encode(any())).thenReturn("encoded-password");
        service = new UserImportService(users, profiles, passwords, transactions);
    }

    @Test
    void templateIsRealXlsxWithSingleTextHeaderAndNoData() throws Exception {
        var template = service.template();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(Base64.getDecoder().decode(template.contentBase64())))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 8);
            String[] headers = {"学号", "姓名", "手机号", "学院", "校区", "宿舍楼", "昵称", "初始密码"};
            for (int i = 0; i < 8; i++) {
                assertThat(sheet.getRow(0).getCell(i).getStringCellValue()).isEqualTo(headers[i]);
                assertThat(sheet.getColumnStyle(i).getDataFormatString()).isEqualTo("@");
            }
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
        }
        assertThat(service.preview(new UserImportDto.FileRequest(template.fileName(), template.contentBase64())).total()).isZero();
        verifyNoInteractions(users, profiles, passwords);
    }

    @Test
    void previewPreservesLeadingZerosSkipsBlankRowsAndNeverExposesPassword() throws Exception {
        var file = file(sheet -> {
            row(sheet, 1, "00001234", " 张三 ", "13800000000", "学院", "校区", "宿舍", "", " secret ");
            row(sheet, 2, " ", "", "", "", "", "", "", "");
            row(sheet, 5, "Ab1234", "李四", "", "", "", "", "昵称", "abcdef");
        });
        var result = service.preview(file);
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.createCount()).isEqualTo(2);
        assertThat(result.errorCount()).isZero();
        assertThat(result.rows().get(0).studentNo()).isEqualTo("00001234");
        assertThat(result.rows().get(0).realName()).isEqualTo("张三");
        assertThat(result.rows().get(1).rowNumber()).isEqualTo(6);
        assertThat(result.committed()).isFalse();
        assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("secret", "password", "abcdef");
        assertThat(file.toString()).doesNotContain(file.contentBase64());
        verify(users, never()).insert(any(User.class));
        verifyNoInteractions(profiles, passwords, transactions);
    }

    @Test
    void createsAccountWithDefaultsAndUntrimmedPassword() throws Exception {
        var result = service.commit(file(sheet -> row(sheet, 1,
                "00001234", "张三", "", "", "", "", "", " pass ")));
        assertThat(result.committed()).isTrue();
        var captor = ArgumentCaptor.forClass(User.class);
        verify(users).insert(captor.capture());
        User user = captor.getValue();
        assertThat(user.getStudentNo()).isEqualTo("00001234");
        assertThat(user.getNickname()).isEqualTo("同学1234");
        assertThat(user.getPhone()).isNull();
        assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(user.getAuthSource()).isEqualTo(4);
        assertThat(user.getVerifyStatus()).isEqualTo(User.VERIFY_PENDING);
        assertThat(user.getCreditScore()).isEqualTo(100);
        assertThat(user.getOrderCount()).isZero();
        assertThat(user.getStatus()).isEqualTo(User.STATUS_NORMAL);
        verify(passwords).encode(" pass ");
        verify(transactions).commit(status);
        verify(transactions, never()).rollback(any());
    }

    @Test
    void updatesOnlyNonemptyProfileFieldsUsingExistingService() throws Exception {
        when(users.selectOne(any())).thenReturn(existing());
        var result = service.commit(file(sheet -> row(sheet, 1,
                "Ab1234", "新姓名", "", "新学院", "", "", "", "")));
        assertThat(result.updateCount()).isEqualTo(1);
        assertThat(result.committed()).isTrue();
        verify(profiles).updateProfile(42L, new PlatformUserDto.ProfileUpdateRequest(
                null, "新姓名", null, null, "新学院", null, null));
        verify(users, never()).insert(any(User.class));
        verifyNoInteractions(passwords);
    }

    @Test
    void existingPasswordIsRejectedEvenWhenOnlyWhitespace() throws Exception {
        when(users.selectOne(any())).thenReturn(existing());
        var result = service.commit(file(sheet -> row(sheet, 1,
                "Ab1234", "", "", "", "", "", "", " ")));
        assertThat(result.errorCount()).isEqualTo(1);
        assertThat(result.rows().get(0).errors()).contains("已有用户初始密码必须留空，导入不会修改密码");
        assertThat(result.committed()).isFalse();
        verifyNoInteractions(profiles, passwords);
    }

    @Test
    void marksAllCaseInsensitiveStudentAndPhoneDuplicatesAndWritesNothing() throws Exception {
        var result = service.commit(file(sheet -> {
            row(sheet, 1, "Ab1234", "张三", "13800000000", "", "", "", "", "abcdef");
            row(sheet, 2, "aB1234", "李四", "13800000000", "", "", "", "", "abcdef");
        }));
        assertThat(result.errorCount()).isEqualTo(2);
        assertThat(result.rows()).allSatisfy(row -> assertThat(row.errors()).contains("文件内学号重复", "文件内手机号重复"));
        assertThat(result.committed()).isFalse();
        verify(users, never()).insert(any(User.class));
        verifyNoInteractions(profiles, passwords);
    }

    @Test
    void phoneOwnedByAnotherUserIsRejected() throws Exception {
        when(users.selectOne(any())).thenReturn(null, existing());
        var result = service.preview(file(sheet -> row(sheet, 1,
                "Ab1234", "张三", "13800000000", "", "", "", "", "abcdef")));
        assertThat(result.rows().get(0).errors()).contains("手机号已被其他用户使用");
    }

    @Test
    void existingUserMayKeepOwnPhone() throws Exception {
        when(users.selectOne(any())).thenReturn(existing());
        var result = service.preview(file(sheet -> row(sheet, 1,
                "Ab1234", "", "13800000000", "", "", "", "", "")));
        assertThat(result.errorCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void validatesEveryColumnLength(int column) throws Exception {
        String[] values = {"Ab1234", "张三", "13800000000", "学院", "校区", "宿舍", "昵称", "abcdef"};
        int[] lengths = {32, 32, 20, 64, 64, 64, 64, 128};
        values[column] = "a".repeat(lengths[column] + 1);
        assertThat(service.preview(file(sheet -> row(sheet, 1, values))).errorCount()).isEqualTo(1);
    }

    @Test
    void rejectsMissingRequiredFieldsAndInvalidPhone() throws Exception {
        var result = service.preview(file(sheet -> row(sheet, 1,
                "", "", "12000000000", "", "", "", "", "12345")));
        assertThat(result.rows().get(0).errors()).hasSize(4);
    }

    @Test
    void rejectsWhitespaceOnlyInitialPassword() throws Exception {
        var result = service.commit(file(sheet -> row(sheet, 1, "Ab1234", "张三", "", "", "", "", "", "      ")));
        assertThat(result.committed()).isFalse();
        assertThat(result.errorCount()).isEqualTo(1);
        verify(users, never()).insert(any(User.class));
    }

    @Test
    void acceptsSchemaCodePointLengthsAndPasswordBoundaries() throws Exception {
        var result = service.preview(file(sheet -> {
            row(sheet, 1, "A".repeat(32), "名".repeat(32), "", "校".repeat(64), "区".repeat(64), "楼".repeat(64), "昵".repeat(64), "x".repeat(128));
            row(sheet, 2, "0001", "名", "", "", "", "", "", "123456");
        }));
        assertThat(result.errorCount()).isZero();
    }

    @Test
    void refusesFormulaErrorAndExtraColumnsWithoutEvaluating() throws Exception {
        var result = service.preview(file(sheet -> {
            row(sheet, 1, "Ab1234", "张三", "", "", "", "", "", "abcdef");
            sheet.getRow(1).getCell(3).setCellFormula("1+1");
            sheet.getRow(1).getCell(4).setCellErrorValue((byte) 7);
            sheet.getRow(1).createCell(8).setCellValue("extra");
        }));
        assertThat(result.rows().get(0).errors()).contains("不允许公式或错误单元格", "存在多余非空列");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void refusesInvalidHeaders(int mode) throws Exception {
        var file = file(sheet -> {
            if (mode == 0) sheet.getRow(0).getCell(0).setCellValue("姓名");
            if (mode == 1) sheet.getRow(0).getCell(0).setCellFormula("1+1");
            if (mode == 2) sheet.getRow(0).createCell(8).setCellValue("多余");
            if (mode == 3) sheet.removeRow(sheet.getRow(0));
        });
        assertThatThrownBy(() -> service.preview(file)).isInstanceOf(BizException.class);
        verifyNoInteractions(users);
    }

    @Test
    void enforces500NonemptyRowsButNotBlankPhysicalRows() throws Exception {
        var valid = file(sheet -> {
            for (int i = 1; i <= 500; i++) row(sheet, i * 2, "S" + (1000 + i), "名", "", "", "", "", "abcdef");
            sheet.createRow(2000);
        });
        assertThat(service.preview(valid).total()).isEqualTo(500);
        var invalid = file(sheet -> {
            for (int i = 1; i <= 501; i++) row(sheet, i, "S" + (1000 + i), "名", "", "", "", "", "abcdef");
        });
        assertThatThrownBy(() -> service.preview(invalid)).isInstanceOf(BizException.class).hasMessageContaining("500");
    }

    @Test
    void rejectsInvalidExtensionBase64AndOversizedContent() {
        assertThatThrownBy(() -> service.preview(new UserImportDto.FileRequest("a.xls", "AAAA"))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.preview(new UserImportDto.FileRequest("a.xlsx", "!!!"))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.preview(new UserImportDto.FileRequest("a.xlsx", "AAAA"))).isInstanceOf(BizException.class);
        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> service.preview(new UserImportDto.FileRequest("a.xlsx", Base64.getEncoder().encodeToString(oversized))))
                .isInstanceOf(BizException.class).hasMessageContaining("2MB");
    }

    @Test
    void rejectsZipBombBeforeOpeningWorkbook() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
            zip.write(new byte[2 * 1024 * 1024]);
            zip.closeEntry();
        }
        var file = new UserImportDto.FileRequest("bomb.xlsx", Base64.getEncoder().encodeToString(output.toByteArray()));
        assertThatThrownBy(() -> service.preview(file)).isInstanceOf(BizException.class).hasMessageContaining("压缩比例");
        verifyNoInteractions(users);
    }

    @Test
    void commitRevalidatesDatabaseInsteadOfTrustingPreview() throws Exception {
        var file = file(sheet -> row(sheet, 1, "Ab1234", "张三", "", "", "", "", "", "abcdef"));
        assertThat(service.preview(file).errorCount()).isZero();
        when(users.selectOne(any())).thenReturn(existing());
        var result = service.commit(file);
        assertThat(result.committed()).isFalse();
        assertThat(result.updateCount()).isEqualTo(1);
        verify(users, never()).insert(any(User.class));
        verifyNoInteractions(profiles, passwords);
    }

    @Test
    void laterInvalidRowPreventsAllWrites() throws Exception {
        var result = service.commit(file(sheet -> {
            row(sheet, 1, "Ab1234", "张三", "", "", "", "", "", "abcdef");
            row(sheet, 2, "Ab1235", "", "", "", "", "", "", "");
        }));
        assertThat(result.committed()).isFalse();
        verify(users, never()).insert(any(User.class));
        verifyNoInteractions(profiles, passwords);
    }

    @Test
    void concurrentDuplicateRollsBackWholeTransactionAndReturnsSafeError() throws Exception {
        when(users.insert(any(User.class))).thenReturn(1).thenThrow(new DuplicateKeyException("secret database detail"));
        var result = service.commit(file(sheet -> {
            row(sheet, 1, "Ab1234", "张三", "", "", "", "", "", "abcdef");
            row(sheet, 2, "Ab1235", "李四", "", "", "", "", "", "abcdef");
        }));
        assertThat(result.committed()).isFalse();
        assertThat(result.errorCount()).isEqualTo(1);
        assertThat(result.rows().get(1).errors().get(0)).contains("全部回滚").doesNotContain("secret");
        verify(transactions).rollback(status);
        verify(transactions, never()).commit(any());
    }

    @Test
    void profileServiceConflictAlsoRollsBack() throws Exception {
        when(users.selectOne(any())).thenReturn(existing());
        when(profiles.updateProfile(eq(42L), any())).thenThrow(new BizException(ErrorCode.USER_PHONE_EXISTS));
        var result = service.commit(file(sheet -> row(sheet, 1, "Ab1234", "新姓名", "", "", "", "", "", "")));
        assertThat(result.committed()).isFalse();
        verify(transactions).rollback(status);
        verify(transactions, never()).commit(any());
    }

    @Test
    void realPasswordHasherStoresOnlyHashAndPreservesSpaces() throws Exception {
        PasswordHasher hasher = new PasswordHasher(null);
        service = new UserImportService(users, profiles, hasher, transactions);
        service.commit(file(sheet -> row(sheet, 1, "Ab1234", "张三", "", "", "", "", "", " pass ")));
        var captor = ArgumentCaptor.forClass(User.class);
        verify(users).insert(captor.capture());
        String hash = captor.getValue().getPasswordHash();
        assertThat(hash).doesNotContain(" pass ");
        assertThat(hasher.matches(" pass ", hash)).isTrue();
        assertThat(hasher.matches("pass", hash)).isFalse();
    }

    @Test
    void honorsNumericLeadingZeroFormatAndIgnoresSecondSheet() throws Exception {
        var file = file(sheet -> {
            row(sheet, 1, "", "张三", "", "", "", "", "", "abcdef");
            var cell = sheet.getRow(1).getCell(0);
            cell.setCellValue(1234);
            var style = sheet.getWorkbook().createCellStyle();
            style.setDataFormat(sheet.getWorkbook().createDataFormat().getFormat("00000000"));
            cell.setCellStyle(style);
            sheet.getWorkbook().getSheetAt(1).getRow(0).getCell(0).setCellFormula("1/0");
            sheet.getRow(1).createCell(10, CellType.BLANK);
        });
        var result = service.preview(new UserImportDto.FileRequest("USERS.XLSX", file.contentBase64()));
        assertThat(result.rows().get(0).studentNo()).isEqualTo("00001234");
        assertThat(result.errorCount()).isZero();
    }

    @Test
    void commitReparsesChangedFile() throws Exception {
        assertThat(service.preview(file(sheet -> row(sheet, 1, "Ab1234", "张三", "", "", "", "", "", "abcdef"))).errorCount()).isZero();
        var result = service.commit(file(sheet -> row(sheet, 1, "Ab1234", "张三", "", "", "", "", "", "")));
        assertThat(result.committed()).isFalse();
        assertThat(result.errorCount()).isEqualTo(1);
        verify(users, never()).insert(any(User.class));
    }

    private User existing() {
        User user = new User();
        user.setId(42L);
        user.setStatus(User.STATUS_BANNED);
        user.setVerifyStatus(User.VERIFY_PASSED);
        user.setPasswordHash("unchanged");
        return user;
    }

    private UserImportDto.FileRequest file(Consumer<Sheet> edit) throws Exception {
        var template = service.template();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(Base64.getDecoder().decode(template.contentBase64())));
             var output = new ByteArrayOutputStream()) {
            edit.accept(workbook.getSheetAt(0));
            workbook.write(output);
            return new UserImportDto.FileRequest("users.xlsx", Base64.getEncoder().encodeToString(output.toByteArray()));
        }
    }

    private void row(Sheet sheet, int index, String... values) {
        var row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) row.createCell(i, CellType.STRING).setCellValue(values[i]);
    }
}
