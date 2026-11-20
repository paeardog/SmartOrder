package com.campus.smartorder.user.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.user.domain.User;
import com.campus.smartorder.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserAvatarService {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private static final int AVATAR_SIZE = 256;
    private static final Pattern FILENAME = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.jpg");
    private final UserMapper userMapper;
    private final Path directory;

    public UserAvatarService(UserMapper userMapper,
                             @Value("${smartorder.avatar-directory:./uploads/avatars}") String directory) {
        this.userMapper = userMapper;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public void updateAvatar(Long userId, MultipartFile file) {
        byte[] jpeg = normalize(file);
        String filename = UUID.randomUUID() + ".jpg";
        Path target = directory.resolve(filename);
        boolean created = false;
        try {
            Files.createDirectories(directory);
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                output.write(jpeg);
            }
            var update = new LambdaUpdateWrapper<User>().eq(User::getId, userId)
                    .set(User::getAvatarUrl, "/api/v1/system/avatars/" + filename)
                    .set(User::getUpdatedAt, LocalDateTime.now());
            if (userMapper.update(null, update) != 1) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "头像保存失败");
            }
        } catch (IOException | RuntimeException ex) {
            var failure = new BizException(ErrorCode.SYSTEM_ERROR, "头像保存失败");
            failure.addSuppressed(ex);
            if (created) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
    }

    public byte[] readAvatar(String filename) {
        if (filename == null || !FILENAME.matcher(filename).matches()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Path target = directory.resolve(filename);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        try (var input = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) {
            return input.readAllBytes();
        } catch (NoSuchFileException ex) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "头像读取失败");
        }
    }

    private byte[] normalize(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new BizException(ErrorCode.INVALID_PARAM, "头像不能为空且不能超过1MB");
        }
        BufferedImage source;
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_BYTES) {
                throw new BizException(ErrorCode.INVALID_PARAM, "头像不能为空且不能超过1MB");
            }
            try (var imageInput = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) {
                    throw new BizException(ErrorCode.INVALID_PARAM, "头像只支持JPEG或PNG图片");
                }
                ImageReader reader = readers.next();
                try {
                    String format = reader.getFormatName();
                    if (!"JPEG".equalsIgnoreCase(format) && !"PNG".equalsIgnoreCase(format)) {
                        throw new BizException(ErrorCode.INVALID_PARAM, "头像只支持JPEG或PNG图片");
                    }
                    reader.setInput(imageInput, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                        throw new BizException(ErrorCode.INVALID_PARAM, "头像尺寸不能超过4096×4096");
                    }
                    source = reader.read(0);
                    if (source == null) {
                        throw new BizException(ErrorCode.INVALID_PARAM, "头像图片无法解码");
                    }
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new BizException(ErrorCode.INVALID_PARAM, "头像图片无法解码");
        }
        BufferedImage avatar = new BufferedImage(AVATAR_SIZE, AVATAR_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = avatar.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, AVATAR_SIZE, AVATAR_SIZE);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int side = Math.min(source.getWidth(), source.getHeight());
            int x = (source.getWidth() - side) / 2;
            int y = (source.getHeight() - side) / 2;
            graphics.drawImage(source, 0, 0, AVATAR_SIZE, AVATAR_SIZE, x, y, x + side, y + side, null);
        } finally {
            graphics.dispose();
            source.flush();
        }
        try (var output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(avatar, "JPEG", output)) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "头像编码失败");
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "头像编码失败");
        } finally {
            avatar.flush();
        }
    }
}
