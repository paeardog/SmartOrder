package com.campus.smartorder.user.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.user.api.PlatformUserDto;
import com.campus.smartorder.user.domain.User;
import com.campus.smartorder.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformUserServiceTest {

    private final UserMapper users = mock(UserMapper.class);
    private final OrderMapper orders = mock(OrderMapper.class);
    private final PlatformUserService service = new PlatformUserService(users, orders);

    @BeforeAll
    static void initializeMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "platform-users"),
                User.class);
    }

    @Test
    void bindsAllFiltersAndEscapesLiteralWildcardsInsideGroupedSearch() {
        String keyword = " A%_!\\' OR 1=1 -- ";
        String pattern = "%A!%!_!!\\' OR 1=1 --%";
        when(users.<Page<User>>selectPage(any(), any())).thenAnswer(invocation -> {
            Page<User> page = invocation.getArgument(0);
            LambdaQueryWrapper<User> query = invocation.getArgument(1);
            assertEquals(3, page.getCurrent());
            assertEquals(20, page.getSize());
            String sql = query.getSqlSegment();
            assertTrue(sql.startsWith("(status = "));
            assertTrue(sql.contains("AND verify_status = "));
            assertTrue(sql.contains("AND (nickname LIKE "));
            assertTrue(sql.contains("OR real_name LIKE "));
            assertTrue(sql.contains("OR student_no LIKE "));
            assertTrue(sql.contains("OR phone LIKE "));
            assertTrue(sql.endsWith("ORDER BY id DESC"));
            assertEquals(4, sql.split("ESCAPE '!'", -1).length - 1);
            assertFalse(sql.contains("OR 1=1"));
            assertEquals(6, query.getParamNameValuePairs().size());
            assertTrue(query.getParamNameValuePairs().containsValue(0));
            assertTrue(query.getParamNameValuePairs().containsValue(1));
            assertEquals(4, query.getParamNameValuePairs().values().stream().filter(pattern::equals).count());
            assertFalse(query.getSqlSelect().contains("password_hash"));
            assertTrue(query.getSqlSelect().contains("dorm_building"));
            assertTrue(query.getSqlSelect().contains("verify_note"));
            assertTrue(query.getSqlSelect().contains("forbidden_reason"));
            return page.setTotal(41).setRecords(List.of(user(User.STATUS_BANNED)));
        });

        var result = service.list(3, 20, keyword, 0, 1);
        assertEquals(41, result.total());
        assertEquals(3, result.page());
        assertEquals(20, result.pageSize());
        var row = result.list().get(0);
        assertEquals("9007199254740993", row.userId());
        assertEquals("小林", row.nickname());
        assertEquals("林同学", row.realName());
        assertEquals("13800000000", row.phone());
        assertEquals("202611180001", row.studentNo());
        assertEquals("计算机学院", row.college());
        assertEquals("北校区", row.campusArea());
        assertEquals("紫荆公寓 3 栋", row.dormBuilding());
        assertEquals(1, row.verifyStatus());
        assertEquals("证件不清晰", row.verifyNote());
        assertEquals(100, row.creditScore());
        assertEquals(5000000000L, row.orderCount());
        assertEquals(0, row.status());
        assertEquals("原封禁原因", row.forbiddenReason());
        assertEquals(LocalDateTime.of(2026, 11, 18, 12, 0), row.createdAt());
        assertNull(row.lastLoginAt());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void omitsEmptyFiltersAndRetainsRequestedPageWhenEmpty(String keyword) {
        when(users.<Page<User>>selectPage(any(), any())).thenAnswer(invocation -> {
            Page<User> page = invocation.getArgument(0);
            LambdaQueryWrapper<User> query = invocation.getArgument(1);
            assertEquals("ORDER BY id DESC", query.getSqlSegment().trim());
            assertTrue(query.getParamNameValuePairs().isEmpty());
            return page.setTotal(2).setRecords(List.of());
        });
        var result = service.list(100, 20, keyword, null, null);
        assertEquals(100, result.page());
        assertEquals(2, result.total());
        assertTrue(result.list().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"0,20,,", "1,0,,", "1,101,,", "1,20,2,", "1,20,,4", "1,20,,-1"})
    void rejectsInvalidListArguments(int page, int pageSize, Integer status, Integer verifyStatus) {
        assertError(ErrorCode.INVALID_PARAM, () -> service.list(page, pageSize, null, status, verifyStatus));
        verifyNoInteractions(users);
    }

    @Test
    void returnsAggregateSummary() {
        var summary = new PlatformUserDto.Summary(10, 7, 3, 2);
        when(users.selectPlatformUserSummary()).thenReturn(summary);
        assertEquals(summary, service.summary());
        verify(users).selectPlatformUserSummary();
    }

    @Test
    void bansWithTrimmedReasonUsingExpectedStatus() {
        when(users.selectById(1L)).thenReturn(user(User.STATUS_NORMAL));
        when(users.compareAndSetStatus(1L, 1, 0, "多次恶意下单")).thenReturn(1);
        var result = service.changeStatus(1L, new PlatformUserDto.StatusRequest(0, "  多次恶意下单  ", 1));
        assertEquals(0, result.status());
        assertEquals("多次恶意下单", result.forbiddenReason());
        verify(users).compareAndSetStatus(1L, 1, 0, "多次恶意下单");
        verify(users, never()).updateById(any(User.class));
    }

    @Test
    void acceptsMaximumReasonLength() {
        String reason = "字".repeat(255);
        when(users.selectById(1L)).thenReturn(user(User.STATUS_NORMAL));
        when(users.compareAndSetStatus(1L, 1, 0, reason)).thenReturn(1);
        assertEquals(reason, service.changeStatus(1L, new PlatformUserDto.StatusRequest(0, reason, 1)).forbiddenReason());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "忽略这段解禁原因"})
    void unbansAndExplicitlyClearsReason(String reason) {
        when(users.selectById(1L)).thenReturn(user(User.STATUS_BANNED));
        when(users.compareAndSetStatus(1L, 0, 1, "")).thenReturn(1);
        var result = service.changeStatus(1L, new PlatformUserDto.StatusRequest(1, reason, 0));
        assertEquals(1, result.status());
        assertEquals("", result.forbiddenReason());
        verify(users).compareAndSetStatus(1L, 0, 1, "");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "\u2003"})
    void requiresReasonToBan(String reason) {
        assertError(ErrorCode.INVALID_PARAM,
                () -> service.changeStatus(1L, new PlatformUserDto.StatusRequest(0, reason, 1)));
        verifyNoInteractions(users);
    }

    @Test
    void rejectsTooLongReason() {
        assertError(ErrorCode.INVALID_PARAM,
                () -> service.changeStatus(1L, new PlatformUserDto.StatusRequest(0, "字".repeat(256), 1)));
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @CsvSource({"-1,1", "2,1", ",1", "0,-1", "0,2", "0,"})
    void rejectsInvalidOrMissingStatuses(Integer status, Integer expectedStatus) {
        assertError(ErrorCode.INVALID_PARAM,
                () -> service.changeStatus(1L, new PlatformUserDto.StatusRequest(status, "原因", expectedStatus)));
        verifyNoInteractions(users);
    }

    @Test
    void rejectsMissingUser() {
        assertError(ErrorCode.RESOURCE_NOT_FOUND,
                () -> service.changeStatus(1L, new PlatformUserDto.StatusRequest(0, "原因", 1)));
        verify(users, never()).compareAndSetStatus(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    void approvesPendingUserWithTrimmedNote() {
        when(users.selectById(1L)).thenReturn(user(User.STATUS_NORMAL));
        when(users.casVerifyStatus(1L, 1, 2, "资料完整")).thenReturn(1);
        var result = service.auditVerify(1L, new PlatformUserDto.VerifyRequest(2, 1, "  资料完整  "));
        assertEquals(2, result.verifyStatus());
        assertEquals("资料完整", result.verifyNote());
        verify(users).casVerifyStatus(1L, 1, 2, "资料完整");
    }

    @Test
    void approvesRejectedUserAgain() {
        User rejected = user(User.STATUS_NORMAL);
        rejected.setVerifyStatus(User.VERIFY_REJECTED);
        when(users.selectById(1L)).thenReturn(rejected);
        when(users.casVerifyStatus(1L, 3, 2, "")).thenReturn(1);
        assertEquals(2, service.auditVerify(1L, new PlatformUserDto.VerifyRequest(2, 3, null)).verifyStatus());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "\u2003"})
    void requiresReasonToReject(String reason) {
        assertError(ErrorCode.INVALID_PARAM,
                () -> service.auditVerify(1L, new PlatformUserDto.VerifyRequest(3, 1, reason)));
        verifyNoInteractions(users);
    }

    @Test
    void rejectsTooLongVerifyNote() {
        assertError(ErrorCode.INVALID_PARAM,
                () -> service.auditVerify(1L, new PlatformUserDto.VerifyRequest(2, 1, "字".repeat(256))));
        verifyNoInteractions(users);
    }

    @Test
    void rejectsStaleVerifyStatusBeforeWriting() {
        when(users.selectById(1L)).thenReturn(user(User.STATUS_NORMAL));
        var error = assertError(ErrorCode.INVALID_PARAM,
                () -> service.auditVerify(1L, new PlatformUserDto.VerifyRequest(2, 0, null)));
        assertTrue(error.getMessage().contains("刷新"));
        verify(users, never()).casVerifyStatus(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    void detectsConcurrentVerifyChange() {
        User user = user(User.STATUS_NORMAL);
        when(users.selectById(1L)).thenReturn(user);
        when(users.casVerifyStatus(1L, 1, 2, "")).thenReturn(0);
        assertError(ErrorCode.INVALID_PARAM,
                () -> service.auditVerify(1L, new PlatformUserDto.VerifyRequest(2, 1, "")));
        assertEquals(User.VERIFY_PENDING, user.getVerifyStatus());
    }

    @Test
    void onlyRejectsPendingUser() {
        User passed = user(User.STATUS_NORMAL);
        passed.setVerifyStatus(User.VERIFY_PASSED);
        when(users.selectById(1L)).thenReturn(passed);
        assertError(ErrorCode.INVALID_PARAM,
                () -> service.auditVerify(1L, new PlatformUserDto.VerifyRequest(3, 2, "原因")));
        verify(users, never()).casVerifyStatus(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    void rejectsInvalidVerifyTransition() {
        assertError(ErrorCode.INVALID_PARAM,
                () -> service.auditVerify(1L, new PlatformUserDto.VerifyRequest(1, 1, null)));
        assertError(ErrorCode.INVALID_PARAM,
                () -> service.auditVerify(1L, new PlatformUserDto.VerifyRequest(2, 4, null)));
        assertError(ErrorCode.INVALID_PARAM,
                () -> service.auditVerify(1L, new PlatformUserDto.VerifyRequest(null, 1, null)));
        verifyNoInteractions(users);
    }

    @Test
    void updatesProfileFieldsAndClearsPhone() {
        User existing = user(User.STATUS_NORMAL);
        User saved = user(User.STATUS_NORMAL);
        saved.setNickname("新昵称");
        saved.setCollege("软件学院");
        saved.setPhone(null);
        when(users.selectById(1L)).thenReturn(existing, saved);
        when(users.selectCount(any())).thenReturn(0L);
        when(users.update(isNull(), any())).thenReturn(1);

        var result = service.updateProfile(1L, new PlatformUserDto.ProfileUpdateRequest(
                "  新昵称  ", null, null, "", "  软件学院  ", null, null));

        assertEquals("新昵称", result.nickname());
        assertEquals("软件学院", result.college());
        assertEquals("", result.phone());
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<User>> captor =
                ArgumentCaptor.forClass((Class) com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(users).update(isNull(), captor.capture());
        var wrapper = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>) captor.getValue();
        String set = wrapper.getSqlSet();
        assertTrue(set.contains("nickname="));
        assertTrue(set.contains("college="));
        assertTrue(set.contains("phone="));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(null));
    }

    @Test
    void rejectsDuplicatePhoneBeforeUpdate() {
        when(users.selectById(1L)).thenReturn(user(User.STATUS_NORMAL));
        when(users.selectCount(any())).thenReturn(1L);
        assertError(ErrorCode.USER_PHONE_EXISTS,
                () -> service.updateProfile(1L,
                        new PlatformUserDto.ProfileUpdateRequest(null, null, null, "13900000000", null, null, null)));
        verify(users, never()).update(isNull(), any());
    }

    @Test
    void rejectsDuplicateStudentNoBeforeUpdate() {
        when(users.selectById(1L)).thenReturn(user(User.STATUS_NORMAL));
        when(users.selectCount(any())).thenReturn(1L);
        assertError(ErrorCode.USER_STUDENT_NO_EXISTS,
                () -> service.updateProfile(1L,
                        new PlatformUserDto.ProfileUpdateRequest(null, null, "20269999", null, null, null, null)));
        verify(users, never()).update(isNull(), any());
    }

    @Test
    void rejectsMalformedPhoneAndStudentNo() {
        when(users.selectById(1L)).thenReturn(user(User.STATUS_NORMAL));
        assertError(ErrorCode.INVALID_PARAM,
                () -> service.updateProfile(1L,
                        new PlatformUserDto.ProfileUpdateRequest(null, null, null, "12345", null, null, null)));
        assertError(ErrorCode.INVALID_PARAM,
                () -> service.updateProfile(1L,
                        new PlatformUserDto.ProfileUpdateRequest(null, null, "学号", null, null, null, null)));
        verify(users, never()).update(isNull(), any());
    }

    @Test
    void deleteRejectsUserWithOrders() {
        when(users.selectById(1L)).thenReturn(user(User.STATUS_NORMAL));
        when(orders.selectCount(any())).thenReturn(3L);
        assertError(ErrorCode.USER_HAS_ORDERS, () -> service.delete(1L));
        verify(users, never()).deleteById(anyLong());
    }

    @Test
    void deletesUserWithoutOrders() {
        when(users.selectById(1L)).thenReturn(user(User.STATUS_NORMAL));
        when(orders.selectCount(any())).thenReturn(0L);
        service.delete(1L);
        verify(users).deleteById(1L);
    }

    @Test
    void rejectsMissingUserForVerifyAndDelete() {
        assertError(ErrorCode.RESOURCE_NOT_FOUND,
                () -> service.auditVerify(1L, new PlatformUserDto.VerifyRequest(2, 1, null)));
        assertError(ErrorCode.RESOURCE_NOT_FOUND, () -> service.delete(1L));
    }

    @Test
    void rejectsStaleExpectedStatusEvenWhenTargetAlreadyMatches() {
        when(users.selectById(1L)).thenReturn(user(User.STATUS_BANNED));
        var error = assertError(ErrorCode.INVALID_PARAM,
                () -> service.changeStatus(1L, new PlatformUserDto.StatusRequest(0, "新原因", 1)));
        assertTrue(error.getMessage().contains("刷新"));
        verify(users, never()).compareAndSetStatus(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    void detectsConcurrentChangeBetweenReadAndUpdate() {
        User user = user(User.STATUS_NORMAL);
        when(users.selectById(1L)).thenReturn(user);
        when(users.compareAndSetStatus(1L, 1, 0, "原因")).thenReturn(0);
        assertError(ErrorCode.INVALID_PARAM,
                () -> service.changeStatus(1L, new PlatformUserDto.StatusRequest(0, "原因", 1)));
        assertEquals(User.STATUS_NORMAL, user.getStatus());
        verify(users, never()).updateById(any(User.class));
    }

    @Test
    void sameStateDoesNotOverwriteExistingBanReason() {
        when(users.selectById(1L)).thenReturn(user(User.STATUS_BANNED));
        assertEquals("原封禁原因",
                service.changeStatus(1L, new PlatformUserDto.StatusRequest(0, "新原因", 0)).forbiddenReason());
        verify(users, never()).compareAndSetStatus(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    void requiresUsableUser() {
        when(users.selectById(1L)).thenReturn(user(User.STATUS_NORMAL));
        assertDoesNotThrow(() -> service.requireUsable(1L));
        when(users.selectById(1L)).thenReturn(user(User.STATUS_BANNED));
        assertError(ErrorCode.ACCOUNT_DISABLED, () -> service.requireUsable(1L));
        assertError(ErrorCode.UNAUTHORIZED, () -> service.requireUsable(2L));
        assertError(ErrorCode.UNAUTHORIZED, () -> service.requireUsable(null));
    }

    private User user(int status) {
        User user = new User();
        user.setId(9007199254740993L);
        user.setNickname("小林");
        user.setRealName("林同学");
        user.setPhone("13800000000");
        user.setStudentNo("202611180001");
        user.setCollege("计算机学院");
        user.setCampusArea("北校区");
        user.setDormBuilding("紫荆公寓 3 栋");
        user.setVerifyStatus(1);
        user.setVerifyNote("证件不清晰");
        user.setCreditScore(100);
        user.setOrderCount(5000000000L);
        user.setStatus(status);
        user.setForbiddenReason(status == 0 ? "原封禁原因" : "");
        user.setCreatedAt(LocalDateTime.of(2026, 11, 18, 12, 0));
        user.setPasswordHash("private-password-hash");
        return user;
    }

    private BizException assertError(ErrorCode code, Runnable action) {
        BizException error = assertThrows(BizException.class, action::run);
        assertEquals(code, error.errorCode());
        return error;
    }
}
