package com.campus.smartorder.system.service;

import com.campus.smartorder.system.domain.SysConfig;
import com.campus.smartorder.system.mapper.SysConfigMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class CampusAreaService {

    private final SysConfigMapper configMapper;

    public CampusAreaService(SysConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public List<String> openAreas() {
        SysConfig config = configMapper.selectByKey("OPEN_CAMPUS_AREAS");
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return List.of();
        }
        return Arrays.stream(config.getConfigValue().split(","))
                .map(String::trim)
                .filter(area -> !area.isEmpty())
                .distinct()
                .toList();
    }
}
