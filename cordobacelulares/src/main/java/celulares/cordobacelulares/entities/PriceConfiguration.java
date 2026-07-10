package celulares.cordobacelulares.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_configuration")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PriceConfiguration {

    public static final Long FIXED_ID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "dolar_billete", precision = 19, scale = 4)
    private BigDecimal dolarBillete;

    @Column(name = "usdt", precision = 19, scale = 4)
    private BigDecimal usdt;

    @Column(name = "transferencia_bancaria", precision = 19, scale = 4)
    private BigDecimal transferenciaBancaria;

    @Column(name = "tarjeta_3_pagos", precision = 19, scale = 4)
    private BigDecimal tarjeta3Pagos;

    @Column(name = "tarjeta_6_pagos", precision = 19, scale = 4)
    private BigDecimal tarjeta6Pagos;

    @Column(name = "tarjeta_12_pagos", precision = 19, scale = 4)
    private BigDecimal tarjeta12Pagos;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
