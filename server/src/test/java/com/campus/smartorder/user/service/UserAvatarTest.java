package com.campus.smartorder.user.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.smartorder.auth.mapper.AuthTokenMapper;
import com.campus.smartorder.auth.mapper.SmsCodeMapper;
import com.campus.smartorder.auth.service.AuthService;
import com.campus.smartorder.auth.service.JwtAuthenticationFilter;
import com.campus.smartorder.auth.service.JwtTokenProvider;
import com.campus.smartorder.auth.service.PasswordHasher;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.GlobalExceptionHandler;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.console.ConsoleAccountService;
import com.campus.smartorder.user.api.UserAvatarController;
import com.campus.smartorder.user.domain.User;
import com.campus.smartorder.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserAvatarTest {
    @TempDir
    Path temporary;
    private Path directory;
    private UserMapper users;
    private UserAvatarService avatars;
    private User user;
    private LambdaUpdateWrapper<User> update;
    private MockMvc mvc;
    private JwtTokenProvider tokens;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "user-avatar"), User.class);
        users = mock(UserMapper.class);
        directory = temporary.resolve("avatars");
        avatars = new UserAvatarService(users, directory.toString());
        var properties = new SmartOrderProperties("Asia/Shanghai", null, null,
                new SmartOrderProperties.Security(Duration.ofHours(1), Duration.ofDays(7),
                        "user-avatar-test-signing-key-long-enough", "test", ""), null);
        tokens = new JwtTokenProvider(properties);
        AuthService auth = new AuthService(users, mock(SmsCodeMapper.class), mock(AuthTokenMapper.class),
                tokens, mock(PasswordHasher.class), mock(ConsoleAccountService.class));
        user = new User();
        user.setId(1L);
        user.setStatus(User.STATUS_NORMAL);
        user.setNickname("同学");
        user.setRealName("小林");
        user.setStudentNo("202611180001");
        user.setCollege("计算机学院");
        user.setVerifyStatus(User.VERIFY_PASSED);
        user.setVerifyNote("已认证");
        user.setPasswordHash("private-hash");
        when(users.selectById(1L)).thenReturn(user);
        when(users.update(isNull(), any())).thenAnswer(invocation -> {
            update = invocation.getArgument(1);
            user.setAvatarUrl((String) assigned("avatar_url"));
            return 1;
        });
        mvc = MockMvcBuilders.standaloneSetup(new UserAvatarController(auth, avatars))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new JwtAuthenticationFilter(tokens, new ObjectMapper(), mock(PlatformUserService.class), mock(com.campus.smartorder.console.ConsoleAccountService.class, org.mockito.Answers.RETURNS_DEEP_STUBS)))
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PNG", "JPEG"})
    void persistsNormalizedImageAndReturnsFreshUnchangedProfile(String format) throws Exception {
        byte[] bytes = image(format, 512, 256, false);
        mvc.perform(multipart("/api/v1/auth/avatar").file(file(bytes))
                        .header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value("1"))
                .andExpect(jsonPath("$.data.nickname").value("同学"))
                .andExpect(jsonPath("$.data.realName").value("小林"))
                .andExpect(jsonPath("$.data.studentNo").value("202611180001"))
                .andExpect(jsonPath("$.data.college").value("计算机学院"))
                .andExpect(jsonPath("$.data.verifyStatus").value(User.VERIFY_PASSED))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.avatarUrl").value(user.getAvatarUrl()));
        String url = user.getAvatarUrl();
        assertNotNull(url);
        assertTrue(url.matches("/api/v1/system/avatars/[0-9a-f-]{36}\\.jpg"));
        assertEquals(2, update.getSqlSet().split(",").length);
        assertInstanceOf(LocalDateTime.class, assigned("updated_at"));
        assertTrue(update.getSqlSegment().contains("id ="));
        assertTrue(update.getParamNameValuePairs().containsValue(1L));
        verify(users, times(2)).selectById(1L);
        verify(users, never()).updateById(any(User.class));
        assertEquals("已认证", user.getVerifyNote());
        assertEquals("private-hash", user.getPasswordHash());
        String filename = url.substring(url.lastIndexOf('/') + 1);
        byte[] saved = Files.readAllBytes(directory.resolve(filename));
        assertArrayEquals(saved, new UserAvatarService(users, directory.toString()).readAvatar(filename));
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(saved));
        assertEquals(256, result.getWidth());
        assertEquals(256, result.getHeight());
        assertFalse(result.getColorModel().hasAlpha());
        Color center = new Color(result.getRGB(128, 128));
        assertTrue(center.getGreen() > 200 && center.getRed() < 30);
        Color left = new Color(result.getRGB(8, 128));
        assertTrue(left.getGreen() > 200 && left.getRed() < 30);
        mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"))
                .andExpect(content().bytes(saved));
    }

    @Test
    void compositesTransparencyOnWhiteAndStripsMetadata() throws Exception {
        byte[] png = image("PNG", 256, 256, true);
        byte[] metadata = "Comment\0private-avatar-metadata".getBytes(StandardCharsets.ISO_8859_1);
        var output = new ByteArrayOutputStream();
        output.write(png, 0, 33);
        output.write(ByteBuffer.allocate(4).putInt(metadata.length).array());
        byte[] type = "tEXt".getBytes(StandardCharsets.US_ASCII);
        output.write(type);
        output.write(metadata);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(metadata);
        output.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        output.write(png, 33, png.length - 33);
        avatars.updateAvatar(1L, file(output.toByteArray()));
        byte[] saved = avatars.readAvatar(Path.of(user.getAvatarUrl()).getFileName().toString());
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(saved));
        assertEquals(Color.WHITE.getRGB(), result.getRGB(0, 0));
        assertFalse(new String(saved, StandardCharsets.ISO_8859_1).contains("private-avatar-metadata"));
    }

    @Test
    void rejectsEmptyMissingAndOversizedFiles() throws Exception {
        assertInvalid(null);
        assertInvalid(file(new byte[0]));
        assertInvalid(file(new byte[1024 * 1024 + 1]));
        mvc.perform(multipart("/api/v1/auth/avatar").header("Authorization", bearer()))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAM.code()));
        verify(users, never()).update(isNull(), any());
    }

    @Test
    void enforcesActualStreamLimitEvenWithFalseReportedSize() throws Exception {
        MultipartFile upload = mock(MultipartFile.class);
        when(upload.getSize()).thenReturn(1L);
        when(upload.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[1024 * 1024 + 1]));
        assertInvalid(upload);
    }

    @Test
    void acceptsExactlyOneMegabyte() throws Exception {
        byte[] padded = Arrays.copyOf(image("JPEG", 256, 256, false), 1024 * 1024);
        avatars.updateAvatar(1L, file(padded));
        assertNotNull(update);
    }

    @Test
    void rejectsTextSvgGifWebpAndTruncatedPngRegardlessOfMime() throws Exception {
        assertInvalid(file("not an image".getBytes(StandardCharsets.UTF_8)));
        assertInvalid(file("<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8)));
        assertInvalid(file(image("GIF", 10, 10, false)));
        assertInvalid(file("RIFF0000WEBPVP8 ".getBytes(StandardCharsets.US_ASCII)));
        assertInvalid(file(Arrays.copyOf(image("PNG", 32, 32, false), 33)));
        mvc.perform(multipart("/api/v1/auth/avatar").file(file("<svg/>".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer()))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAM.code()));
    }

    @ParameterizedTest
    @CsvSource({"4097,1", "1,4097", "50000,50000"})
    void rejectsPixelBombBeforeDecoding(int width, int height) throws Exception {
        byte[] png = image("PNG", 1, 1, false);
        ByteBuffer.wrap(png).putInt(16, width).putInt(20, height);
        CRC32 crc = new CRC32();
        crc.update(png, 12, 17);
        ByteBuffer.wrap(png).putInt(29, (int) crc.getValue());
        BizException error = assertThrows(BizException.class, () -> avatars.updateAvatar(1L, file(png)));
        assertEquals(ErrorCode.INVALID_PARAM, error.errorCode());
        assertTrue(error.getMessage().contains("4096"));
        assertFalse(Files.exists(directory));
    }

    @Test
    void acceptsMaximumDimension() throws Exception {
        avatars.updateAvatar(1L, file(image("PNG", 4096, 1, false)));
        assertNotNull(update);
    }

    @Test
    void rejectsMissingAuthenticationAndAccountTokens() throws Exception {
        var upload = file(image("PNG", 16, 16, false));
        mvc.perform(multipart("/api/v1/auth/avatar").file(upload))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/auth/avatar").file(upload)
                        .header("Authorization", "Bearer " + tokens.createAccountAccessToken(1L, "PLATFORM_ADMIN", null)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.code()));
        verifyNoInteractions(users);
        assertFalse(Files.exists(directory));
    }

    @Test
    void requiresExistingUsableUserBeforeWriting() throws Exception {
        var upload = file(image("PNG", 16, 16, false));
        when(users.selectById(1L)).thenReturn(null);
        mvc.perform(multipart("/api/v1/auth/avatar").file(upload).header("Authorization", bearer()))
                .andExpect(status().isUnauthorized());
        when(users.selectById(1L)).thenReturn(user);
        user.setStatus(User.STATUS_BANNED);
        mvc.perform(multipart("/api/v1/auth/avatar").file(upload).header("Authorization", bearer()))
                .andExpect(status().isForbidden());
        verify(users, never()).update(isNull(), any());
        assertFalse(Files.exists(directory));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../secret.jpg", "..\\secret.jpg", "%2e%2e%2fsecret.jpg", "plain.jpg", "file.svg", "C:\\secret.jpg", "00000000-0000-4000-8000-000000000000.jpg"})
    void invalidAndMissingFilenamesAreNotFound(String filename) {
        BizException error = assertThrows(BizException.class, () -> avatars.readAvatar(filename));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.errorCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"../secret.jpg", "..%2fsecret.jpg", "%2e%2e%5csecret.jpg", "plain.jpg", "00000000-0000-4000-8000-000000000000.jpg"})
    void publicMissingAndTraversalRequestsReturn404(String filename) throws Exception {
        mvc.perform(get("/api/v1/system/avatars/" + filename)).andExpect(status().isNotFound());
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void databaseFailureDeletesOnlyNewFile(boolean throwsException) throws Exception {
        avatars.updateAvatar(1L, file(image("PNG", 16, 16, false)));
        Path previous = directory.resolve(Path.of(user.getAvatarUrl()).getFileName());
        byte[] original = Files.readAllBytes(previous);
        if (throwsException) {
            doThrow(new DataAccessResourceFailureException("database unavailable")).when(users).update(isNull(), any());
        } else {
            doReturn(0).when(users).update(isNull(), any());
        }
        BizException error = assertThrows(BizException.class,
                () -> avatars.updateAvatar(1L, file(image("JPEG", 16, 16, false))));
        assertEquals(ErrorCode.SYSTEM_ERROR, error.errorCode());
        assertArrayEquals(original, Files.readAllBytes(previous));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void filesystemFailureDoesNotUpdateDatabase() throws Exception {
        Files.writeString(directory, "not a directory");
        BizException error = assertThrows(BizException.class,
                () -> avatars.updateAvatar(1L, file(image("PNG", 16, 16, false))));
        assertEquals(ErrorCode.SYSTEM_ERROR, error.errorCode());
        verify(users, never()).update(isNull(), any());
        assertEquals("not a directory", Files.readString(directory));
    }

    @Test
    void directoryWithValidFilenameIsNotServed() throws Exception {
        String filename = UUID.randomUUID() + ".jpg";
        Files.createDirectories(directory.resolve(filename));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND,
                assertThrows(BizException.class, () -> avatars.readAvatar(filename)).errorCode());
    }

    @Test
    void multipartResolverSizeFailureUsesApiResponse() throws Exception {
        var resolver = mock(org.springframework.web.multipart.MultipartResolver.class);
        when(resolver.isMultipart(any())).thenReturn(true);
        when(resolver.resolveMultipart(any())).thenThrow(new MaxUploadSizeExceededException(1024 * 1024));
        try (var context = new org.springframework.web.context.support.AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new org.springframework.mock.web.MockServletContext());
            context.register(MultipartTestConfiguration.class);
            context.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("multipartResolver", resolver));
            context.refresh();
            var resolverMvc = MockMvcBuilders.webAppContextSetup(context).build();
            resolverMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/v1/auth/avatar").contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAM.code()));
            verify(resolver).resolveMultipart(any());
        }
    }

    @org.springframework.context.annotation.Configuration
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    static class MultipartTestConfiguration {
        @org.springframework.context.annotation.Bean
        GlobalExceptionHandler exceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }

    private void assertInvalid(MultipartFile file) {
        BizException error = assertThrows(BizException.class, () -> avatars.updateAvatar(1L, file));
        assertEquals(ErrorCode.INVALID_PARAM, error.errorCode());
        assertNull(update);
        assertFalse(Files.exists(directory));
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "../../untrusted.jpg", "image/jpeg", bytes);
    }

    private byte[] image(String format, int width, int height, boolean transparent) throws Exception {
        BufferedImage image = new BufferedImage(width, height,
                transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        if (!transparent) {
            var graphics = image.createGraphics();
            try {
                graphics.setColor(Color.RED);
                graphics.fillRect(0, 0, width, height);
                graphics.setColor(Color.GREEN);
                int side = Math.min(width, height);
                graphics.fillRect((width - side) / 2, (height - side) / 2, side, side);
            } finally {
                graphics.dispose();
            }
        }
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }

    private String bearer() {
        return "Bearer " + tokens.createAccessToken(1L);
    }

    private Object assigned(String column) {
        var matcher = Pattern.compile("(?:^|,)" + column + "=#\\{ew.paramNameValuePairs.([^}]+)}")
                .matcher(update.getSqlSet());
        assertTrue(matcher.find(), update.getSqlSet());
        return update.getParamNameValuePairs().get(matcher.group(1));
    }
}
