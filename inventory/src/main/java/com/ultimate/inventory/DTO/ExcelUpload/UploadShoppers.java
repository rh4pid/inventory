package com.ultimate.inventory.DTO.ExcelUpload;

import com.ultimate.inventory.utils.ExcelColumn;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UploadShoppers {
    @ExcelColumn("UserEmail")
    private String userEmail;

    @ExcelColumn("ShopCode")
    private String shopCode;

    @ExcelColumn("role")
    private String roles;
}
