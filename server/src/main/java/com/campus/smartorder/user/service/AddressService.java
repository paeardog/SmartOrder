package com.campus.smartorder.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.user.api.AddressDto;
import com.campus.smartorder.user.domain.PickupPoint;
import com.campus.smartorder.user.domain.UserAddress;
import com.campus.smartorder.user.mapper.PickupPointMapper;
import com.campus.smartorder.user.mapper.UserAddressMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class AddressService {

    public static final int MAX_ADDRESS = 20;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final int CONTACT_NAME_MAX = 32;
    private static final int CAMPUS_AREA_MAX = 64;
    private static final int BUILDING_MAX = 64;
    private static final int ROOM_NO_MAX = 32;
    private static final int TAG_MAX = 16;
    private static final int REMARK_MAX = 255;
    private static final DateTimeFormatter SERVER_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");

    private final UserAddressMapper addressMapper;
    private final PickupPointMapper pickupPointMapper;
    private final ZoneId zone;

    public AddressService(UserAddressMapper addressMapper, PickupPointMapper pickupPointMapper,
                          SmartOrderProperties properties) {
        this.addressMapper = addressMapper;
        this.pickupPointMapper = pickupPointMapper;
        this.zone = properties.zone();
    }

    public List<AddressDto.Item> list(Long userId) {
        return toItems(liveRows(userId));
    }

    @Transactional
    public List<AddressDto.Item> create(Long userId, AddressDto.Input input) {
        List<UserAddress> rows = liveRows(userId);
        if (rows.size() >= MAX_ADDRESS) {
            throw BizException.of(ErrorCode.INVALID_PARAM,
                    "地址簿最多保存 " + MAX_ADDRESS + " 条，请先删除一条再新增");
        }
        Values values = validate(input);
        boolean asDefault = input.isDefault() == null ? rows.isEmpty() : input.isDefault();
        if (asDefault) {
            addressMapper.clearDefaultOf(userId);
        }

        UserAddress row = new UserAddress();
        row.setUserId(userId);
        row.setContactName(values.contactName());
        row.setContactPhone(values.contactPhone());
        row.setCampusArea(values.campusArea());
        row.setBuilding(values.building());
        row.setRoomNo(values.roomNo());
        row.setTag(values.tag());
        row.setRemark(values.remark());
        row.setIsDefault(asDefault ? UserAddress.DEFAULT_ON : UserAddress.DEFAULT_OFF);
        addressMapper.insert(row);
        return toItems(liveRows(userId));
    }

    @Transactional
    public List<AddressDto.Item> update(Long userId, Long addressId, AddressDto.Input input) {
        UserAddress row = requireOwned(userId, addressId);
        Values values = validate(input);
        row.setContactName(values.contactName());
        row.setContactPhone(values.contactPhone());
        row.setCampusArea(values.campusArea());
        row.setBuilding(values.building());
        row.setRoomNo(values.roomNo());
        row.setTag(values.tag());
        row.setRemark(values.remark());
        if (input.isDefault() != null) {
            boolean asDefault = input.isDefault();
            if (asDefault) {
                addressMapper.clearDefaultExcept(userId, addressId);
            }
            row.setIsDefault(asDefault ? UserAddress.DEFAULT_ON : UserAddress.DEFAULT_OFF);
        }
        addressMapper.updateContent(row);
        return toItems(liveRows(userId));
    }

    @Transactional
    public List<AddressDto.Item> remove(Long userId, Long addressId) {
        UserAddress row = requireOwned(userId, addressId);
        boolean wasDefault = row.defaultFlag();
        addressMapper.softDelete(addressId, userId);
        if (wasDefault) {
            UserAddress successor = promoteCandidate(userId);
            if (successor != null) {
                addressMapper.markDefault(successor.getId(), userId);
            }
        }
        return toItems(liveRows(userId));
    }

    @Transactional
    public List<AddressDto.Item> setDefault(Long userId, Long addressId) {
        requireOwned(userId, addressId);
        addressMapper.clearDefaultOf(userId);
        addressMapper.markDefault(addressId, userId);
        return toItems(liveRows(userId));
    }

    public UserAddress requireDeliveryAddress(Long userId, Long addressId, Long pickupPointId) {
        UserAddress address = requireOwned(userId, addressId);
        PickupPoint point = pickupPointId == null ? null : pickupPointMapper.selectById(pickupPointId);
        String campusArea = trimmed(address.getCampusArea());
        if (point == null || campusArea.isEmpty() || !campusArea.equals(trimmed(point.getCampusArea()))) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "配送地址与商家不在同一校区，请重新选择地址");
        }
        return address;
    }

    public UserAddress requireOwned(Long userId, Long addressId) {
        if (userId == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        if (addressId == null) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "地址 id 不能为空");
        }
        UserAddress row = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, addressId)
                .eq(UserAddress::getUserId, userId)
                .isNull(UserAddress::getDeletedAt)
                .last("LIMIT 1"));
        if (row == null) {
            throw BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "地址不存在或已删除");
        }
        return row;
    }

    private List<UserAddress> liveRows(Long userId) {
        return addressMapper.selectList(orderWrapper(userId));
    }

    private UserAddress promoteCandidate(Long userId) {
        return addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .isNull(UserAddress::getDeletedAt)
                .orderByDesc(UserAddress::getUpdatedAt)
                .orderByDesc(UserAddress::getId)
                .last("LIMIT 1"));
    }

    private LambdaQueryWrapper<UserAddress> orderWrapper(Long userId) {
        return new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .isNull(UserAddress::getDeletedAt)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getUpdatedAt)
                .orderByDesc(UserAddress::getId);
    }

    private List<AddressDto.Item> toItems(List<UserAddress> rows) {
        return rows.stream().map(this::toItem).toList();
    }

    private AddressDto.Item toItem(UserAddress row) {
        return new AddressDto.Item(
                Ids.of(row.getId()),
                Ids.blank(row.getContactName()),
                Ids.blank(row.getContactPhone()),
                Ids.blank(row.getCampusArea()),
                Ids.blank(row.getBuilding()),
                Ids.blank(row.getRoomNo()),
                Ids.blank(row.getTag()),
                Ids.blank(row.getRemark()),
                row.defaultFlag(),
                stamp(row.getUpdatedAt()));
    }

    private String stamp(LocalDateTime value) {
        return value == null ? "" : value.atZone(zone).format(SERVER_TIME_FORMAT);
    }

    private Values validate(AddressDto.Input input) {
        if (input == null) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "请填写联系人");
        }
        String contactName = trimmed(input.contactName());
        String contactPhone = trimmed(input.contactPhone());
        String campusArea = trimmed(input.campusArea());
        String building = trimmed(input.building());
        String roomNo = trimmed(input.roomNo());
        String tag = trimmed(input.tag());
        String remark = trimmed(input.remark());

        if (contactName.isEmpty()) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "请填写联系人");
        }
        if (!PHONE_PATTERN.matcher(contactPhone).matches()) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "手机号格式不正确");
        }
        if (campusArea.isEmpty()) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "请选择校区");
        }
        if (building.isEmpty()) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "请填写楼栋");
        }
        if (roomNo.isEmpty()) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "请填写寝室号");
        }
        requireLength(contactName, CONTACT_NAME_MAX, "联系人");
        requireLength(campusArea, CAMPUS_AREA_MAX, "校区");
        requireLength(building, BUILDING_MAX, "楼栋");
        requireLength(roomNo, ROOM_NO_MAX, "寝室号");
        requireLength(tag, TAG_MAX, "标签");
        requireLength(remark, REMARK_MAX, "备注");
        return new Values(contactName, contactPhone, campusArea, building, roomNo, tag, remark);
    }

    private void requireLength(String value, int max, String label) {
        if (value.length() > max) {
            throw BizException.of(ErrorCode.INVALID_PARAM, label + "不能超过 " + max + " 个字");
        }
    }

    private String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private record Values(String contactName, String contactPhone, String campusArea, String building,
                          String roomNo, String tag, String remark) {
    }
}
