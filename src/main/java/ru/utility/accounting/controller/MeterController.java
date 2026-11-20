package ru.utility.accounting.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.utility.accounting.dto.MeterDto;
import ru.utility.accounting.entity.Meter;
import ru.utility.accounting.entity.User;
import ru.utility.accounting.repository.UserRepository;
import ru.utility.accounting.service.MeterService;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/meters")
public class MeterController {

    private final MeterService meterService;
    private final UserRepository userRepository;

    public MeterController(MeterService meterService, UserRepository userRepository) {
        this.meterService = meterService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<MeterDto>> getAllMeters(Authentication authentication) {
        boolean isStaff = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_OPERATOR"));

        List<Meter> list;
        if (isStaff) {
            list = meterService.findAll();
        } else {
            User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);
            if (currentUser != null) {
                list = meterService.findByUserId(currentUser.getId());
            } else {
                list = List.of();
            }
        }

        List<MeterDto> dtoList = new ArrayList<>();
        for (Meter m : list) {
            dtoList.add(toDto(m));
        }
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN') or @securityService.isMeterOwner(authentication, #id)")
    public ResponseEntity<MeterDto> getMeterById(@PathVariable Long id) {
        Meter meter = meterService.findById(id);
        return ResponseEntity.ok(toDto(meter));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<MeterDto> createMeter(@RequestBody MeterDto dto) {
        Meter meter = toEntity(dto);
        Meter created = meterService.create(meter, dto.getPremisesId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<MeterDto> patchMeter(@PathVariable Long id, @RequestBody MeterDto dto) {
        Meter updated = meterService.patch(id, dto);
        return ResponseEntity.ok(toDto(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteMeter(@PathVariable Long id) {
        meterService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private MeterDto toDto(Meter meter) {
        if (meter == null) {
            return null;
        }
        Long premisesId = (meter.getPremises() != null) ? meter.getPremises().getId() : null;
        return new MeterDto(
                meter.getId(),
                premisesId,
                meter.getResourceType(),
                meter.getSerialNumber(),
                meter.getUnitRate(),
                meter.getStatus(),
                meter.getCreatedAt()
        );
    }

    private Meter toEntity(MeterDto dto) {
        if (dto == null) {
            return null;
        }
        Meter meter = new Meter();
        meter.setId(dto.getId());
        meter.setResourceType(dto.getResourceType());
        meter.setSerialNumber(dto.getSerialNumber());
        meter.setUnitRate(dto.getUnitRate());
        meter.setStatus(dto.getStatus());
        return meter;
    }
}
