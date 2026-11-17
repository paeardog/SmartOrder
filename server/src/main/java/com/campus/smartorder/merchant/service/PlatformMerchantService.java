package com.campus.smartorder.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlatformMerchantService {

    private final MerchantMapper merchantMapper;
    private final MerchantProfileService profileService;

    public PlatformMerchantService(MerchantMapper merchantMapper, MerchantProfileService profileService) {
        this.merchantMapper = merchantMapper;
        this.profileService = profileService;
    }

    public List<Merchant> merchants(Integer status) {
        if (status != null && (status < Merchant.STATUS_PENDING || status > Merchant.STATUS_REJECTED)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "档口状态只能在 0 到 4 之间");
        }
        return merchantMapper.selectList(new LambdaQueryWrapper<Merchant>()
                .isNull(Merchant::getDeletedAt)
                .eq(status != null, Merchant::getStatus, status)
                .orderByAsc(Merchant::getId));
    }

    @Transactional
    public Merchant approve(Long merchantId) {
        Merchant merchant = profileService.require(merchantId);
        Integer status = merchant.getStatus();
        if (!Integer.valueOf(Merchant.STATUS_PENDING).equals(status)
                && !Integer.valueOf(Merchant.STATUS_REJECTED).equals(status)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "只有待审核或已驳回的档口才能通过");
        }
        return transition(merchant, status, Merchant.STATUS_OPEN, "");
    }

    @Transactional
    public Merchant reject(Long merchantId, String reason) {
        String text = reason == null ? "" : reason.strip();
        if (text.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "驳回必须填写原因");
        }
        if (text.codePointCount(0, text.length()) > 255) {
            throw new BizException(ErrorCode.INVALID_PARAM, "驳回原因不能超过 255 字符");
        }
        Merchant merchant = profileService.require(merchantId);
        if (!Integer.valueOf(Merchant.STATUS_PENDING).equals(merchant.getStatus())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "只有待审核的档口才能驳回");
        }
        return transition(merchant, Merchant.STATUS_PENDING, Merchant.STATUS_REJECTED, text);
    }

    @Transactional
    public Merchant changeStatus(Long merchantId, Integer status, Integer expectedStatus) {
        if (!managedStatus(status) || !managedStatus(expectedStatus)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "状态和原状态只能是正常、停用或封禁");
        }
        if (status.equals(expectedStatus)
                || (expectedStatus == Merchant.STATUS_BANNED && status != Merchant.STATUS_OPEN)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "不支持该档口状态转换");
        }
        Merchant merchant = profileService.require(merchantId);
        if (!expectedStatus.equals(merchant.getStatus())) {
            throw conflict();
        }
        return transition(merchant, expectedStatus, status, null);
    }

    private boolean managedStatus(Integer status) {
        return status != null && status >= Merchant.STATUS_OPEN && status <= Merchant.STATUS_BANNED;
    }

    private Merchant transition(Merchant merchant, Integer expectedStatus, Integer status, String rejectReason) {
        if (merchantMapper.casStatus(merchant.getId(), expectedStatus, status, rejectReason) != 1) {
            throw conflict();
        }
        merchant.setStatus(status);
        if (rejectReason != null) {
            merchant.setRejectReason(rejectReason);
        }
        return merchant;
    }

    private BizException conflict() {
        return new BizException(ErrorCode.INVALID_PARAM, "档口状态已变化，请刷新后重试");
    }
}
