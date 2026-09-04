package com.ultimacuota.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class SellRequest {
    private BigDecimal precio;
}
