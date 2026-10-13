package com.ultimate.inventory.DTO.ExcelUpload;

import com.ultimate.inventory.utils.ExcelColumn;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UploadShop {
    @ExcelColumn("ShopName")
    private String shopName;

    @ExcelColumn("ShopLocation")
    private String shopLocation;
}
