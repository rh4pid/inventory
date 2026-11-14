package com.ultimate.inventory.Imports;

import com.ultimate.inventory.Constants.AccountPurpose;
import com.ultimate.inventory.Constants.AccountType;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Accounts.Account;
import com.ultimate.inventory.Model.Accounts.Journal;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Shop;
import com.ultimate.inventory.Repository.Accounts.AccountRepository;
import com.ultimate.inventory.Repository.Accounts.JournalRepository;
import com.ultimate.inventory.Repository.ShopRepository;
import com.ultimate.inventory.Service.AccountService;
import com.ultimate.inventory.Service.BusinessAccessHelper;
import com.ultimate.inventory.Service.JournalService;
import com.ultimate.inventory.Service.ShopService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImportsTest {

    /** An .xlsx whose first sheet holds these rows (null = empty cell, Double = number cell). */
    private static MockMultipartFile xlsx(Object[]... rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("Data");
            for (int r = 0; r < rows.length; r++) {
                Row row = s.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    Object v = rows[r][c];
                    if (v instanceof Double d) row.createCell(c).setCellValue(d);
                    else if (v != null) row.createCell(c).setCellValue(v.toString());
                }
            }
            wb.write(out);
            return new MockMultipartFile("file", "data.xlsx", "application/octet-stream", out.toByteArray());
        }
    }

    // ── Reading ──────────────────────────────────────────────────────────────

    private static final List<ImportColumn> COLS = List.of(
            ImportColumn.required("name", "Product name", "", ""),
            ImportColumn.required("cost", "Cost price", "", ""),
            ImportColumn.optional("sku", "SKU", "", ""));

    @Test
    void readsByHeaderInAnyOrderAndSkipsBlankRows() throws Exception {
        ImportResult r = new ImportResult(ImportKind.PRODUCTS, true);
        List<SheetRow> rows = SheetReader.read(xlsx(
                new Object[]{"Cost price *", "Extra", "PRODUCT NAME"},
                new Object[]{6.5, "x", "Cola"},
                new Object[]{null, null, null},
                new Object[]{1200.0, null, "Rice"}), COLS, r);

        assertThat(r.hasErrors()).isFalse();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("name")).isEqualTo("Cola");
        assertThat(rows.get(0).get("cost")).isEqualTo("6.5");
        assertThat(rows.get(1).get("cost")).isEqualTo("1200"); // no ".0"
        assertThat(rows.get(1).number()).isEqualTo(4);         // spreadsheet row numbers
        assertThat(rows.get(1).get("sku")).isNull();
    }

    @Test
    void missingRequiredColumnIsAFileError() throws Exception {
        ImportResult r = new ImportResult(ImportKind.PRODUCTS, true);
        SheetReader.read(xlsx(new Object[]{"Product name"}, new Object[]{"Cola"}), COLS, r);
        assertThat(r.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.row()).isZero();
            assertThat(e.message()).contains("Cost price");
        });
    }

    @Test
    void notAnExcelFile() {
        ImportResult r = new ImportResult(ImportKind.PRODUCTS, true);
        SheetReader.read(new MockMultipartFile("file", "x.xlsx", "text/plain", "hello".getBytes()), COLS, r);
        assertThat(r.getErrors()).singleElement().satisfies(e -> assertThat(e.message()).contains("can't be read"));
    }

    @Test
    void cellsParseNumbersDatesAndChoices() {
        ImportResult r = new ImportResult(ImportKind.ACCOUNTS, true);
        List<ImportColumn> cols = List.of(
                ImportColumn.optional("n", "N", "", ""), ImportColumn.optional("d", "D", "", ""),
                ImportColumn.optional("t", "T", "", ""), ImportColumn.optional("bad", "Bad", "", ""));
        Cells c = new Cells(new SheetRow(2, Map.of("n", "1,250.50", "d", "31/01/2026", "t", "liability", "bad", "abc")), r, cols);
        assertThat(c.decimal("n")).isEqualByComparingTo("1250.50");
        assertThat(c.date("d")).hasToString("2026-01-31");
        assertThat(c.choice("t", AccountType.class, Cells::pretty)).isEqualTo(AccountType.LIABILITY);
        assertThat(c.ok()).isTrue();
        assertThat(c.decimal("bad")).isNull();
        assertThat(c.ok()).isFalse();
        assertThat(r.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.row()).isEqualTo(2);
            assertThat(e.column()).isEqualTo("Bad");
        });
    }

    // ── All or nothing ───────────────────────────────────────────────────────

    private static Business company() {
        Business b = new Business();
        b.setId(7L);
        b.setName("Acme");
        b.setCode("BIZ00007");
        return b;
    }

    private static Shop shop(Business b, String name) {
        Shop s = new Shop();
        s.setId(100L);
        s.setBusiness(b);
        s.setShopName(name);
        s.setShopCode("SHP100" + b.getCode());
        s.setDeleted(false);
        return s;
    }

    @Test
    void anyBadRowMeansNothingIsSaved() throws Exception {
        Business b = company();
        BusinessAccessHelper access = mock(BusinessAccessHelper.class);
        when(access.requireBusiness(7L)).thenReturn(b);
        ShopRepository shopRepo = mock(ShopRepository.class);
        when(shopRepo.findByBusiness(eq(b), any())).thenReturn(new PageImpl<>(List.of(shop(b, "Osu"))));
        ShopService shopService = mock(ShopService.class);
        ImportService service = new ImportService(List.of(new LocationImporter(access, shopRepo, shopService)));

        ImportResult r = service.run(7L, ImportKind.LOCATIONS, xlsx(
                new Object[]{"Location name", "Type"},
                new Object[]{"Madina", "Warehouse"},
                new Object[]{"osu", "Shop"},          // already exists
                new Object[]{"Madina", null},         // twice in the file
                new Object[]{"Tema", "Spaceship"}),   // not a type
                false, "me@x.com");

        assertThat(r.isOk()).isFalse();
        assertThat(r.isSaved()).isFalse();
        assertThat(r.getErrors()).extracting(ImportResult.RowError::row).containsExactly(3, 4, 5);
        verify(shopService, never()).createShop(any(), any());
    }

    @Test
    void checkOnlySavesNothingEvenWhenTheFileIsFine() throws Exception {
        Business b = company();
        BusinessAccessHelper access = mock(BusinessAccessHelper.class);
        when(access.requireBusiness(7L)).thenReturn(b);
        ShopRepository shopRepo = mock(ShopRepository.class);
        when(shopRepo.findByBusiness(eq(b), any())).thenReturn(new PageImpl<>(List.of()));
        ShopService shopService = mock(ShopService.class);
        ImportService service = new ImportService(List.of(new LocationImporter(access, shopRepo, shopService)));

        ImportResult r = service.run(7L, ImportKind.LOCATIONS, xlsx(
                new Object[]{"Location name"}, new Object[]{"Madina"}), true, "me@x.com");

        assertThat(r.isOk()).isTrue();
        assertThat(r.getRows()).isEqualTo(1);
        verify(shopService, never()).createShop(any(), any());
    }

    @Test
    void aFailureWhileSavingReportsTheRowAndForgetsProgress() throws Exception {
        Business b = company();
        BusinessAccessHelper access = mock(BusinessAccessHelper.class);
        when(access.requireBusiness(7L)).thenReturn(b);
        ShopRepository shopRepo = mock(ShopRepository.class);
        when(shopRepo.findByBusiness(eq(b), any())).thenReturn(new PageImpl<>(List.of()));
        ShopService shopService = mock(ShopService.class);
        when(shopService.createShop(argThat(d -> d != null && "Tema".equals(d.getShopName())), any()))
                .thenThrow(new AppException(HttpStatus.CONFLICT, "Boom"));
        ImportService service = new ImportService(List.of(new LocationImporter(access, shopRepo, shopService)));

        ImportResult r = service.run(7L, ImportKind.LOCATIONS, xlsx(
                new Object[]{"Location name"}, new Object[]{"Madina"}, new Object[]{"Tema"}), false, "me@x.com");

        assertThat(r.isSaved()).isFalse();
        assertThat(r.getCreated()).isZero();
        assertThat(r.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.row()).isEqualTo(3);
            assertThat(e.message()).contains("Boom").contains("Nothing was saved");
        });
    }

    // ── Opening balances ─────────────────────────────────────────────────────

    private static Account account(Business b, long id, String code, AccountType type, AccountPurpose purpose) {
        Account a = new Account();
        a.setId(id);
        a.setBusiness(b);
        a.setAccountCode(code);
        a.setAccountName(code);
        a.setAccountType(type);
        a.setAccountPurpose(purpose);
        a.setActive(true);
        return a;
    }

    @Test
    @SuppressWarnings("unchecked")
    void openingBalancesBalanceAgainstCapital() throws Exception {
        Business b = company();
        BusinessAccessHelper access = mock(BusinessAccessHelper.class);
        when(access.requireBusiness(7L)).thenReturn(b);
        AccountRepository accountRepo = mock(AccountRepository.class);
        Account bank = account(b, 1, "1010", AccountType.ASSET, AccountPurpose.BANK);
        Account loan = account(b, 2, "2100", AccountType.LIABILITY, AccountPurpose.OTHER);
        Account stock = account(b, 3, "1200", AccountType.ASSET, AccountPurpose.INVENTORY);
        when(accountRepo.findByBusinessOrderByAccountCodeAsc(b)).thenReturn(List.of(bank, loan, stock));
        JournalRepository journalRepo = mock(JournalRepository.class);
        when(journalRepo.findFirstByBusinessAndReferenceStartingWithOrderByIdAsc(any(), any())).thenReturn(Optional.empty());
        JournalService journals = mock(JournalService.class);
        when(journals.post(any(), anyList(), any())).thenReturn(new Journal());
        ImportService service = new ImportService(List.of(
                new AccountImporter(access, accountRepo, journalRepo, mock(AccountService.class), journals)));

        ImportResult r = service.run(7L, ImportKind.ACCOUNTS, xlsx(
                new Object[]{"Account code", "Account name", "Opening balance", "Balance date"},
                new Object[]{"1010", "Bank", "15000", "2026-01-01"},
                new Object[]{"2100", "Loan", "5000", "2026-01-01"}), false, "me@x.com");

        assertThat(r.getErrors()).isEmpty();
        assertThat(r.isSaved()).isTrue();
        ArgumentCaptor<List<JournalService.EntryLine>> lines = ArgumentCaptor.forClass(List.class);
        verify(journals).post(argThat(src -> src.reference().startsWith("OPENBAL-")
                && src.when().toLocalDate().toString().equals("2026-01-01")), lines.capture(), eq("me@x.com"));
        List<JournalService.EntryLine> l = lines.getValue();
        // Bank debit 15,000; loan credit 5,000; capital credit 10,000.
        assertThat(l).anySatisfy(e -> { assertThat(e.account()).isSameAs(bank); assertThat(e.debit()).isEqualByComparingTo("15000"); });
        assertThat(l).anySatisfy(e -> { assertThat(e.account()).isSameAs(loan); assertThat(e.credit()).isEqualByComparingTo("5000"); });
        assertThat(l).anySatisfy(e -> { assertThat(e.purpose()).isEqualTo(AccountPurpose.CAPITAL); assertThat(e.credit()).isEqualByComparingTo("10000"); });
        BigDecimal dr = l.stream().map(JournalService.EntryLine::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cr = l.stream().map(JournalService.EntryLine::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(dr).isEqualByComparingTo(cr);
    }

    @Test
    void stockAndRevenueCantHaveOpeningBalances() throws Exception {
        Business b = company();
        BusinessAccessHelper access = mock(BusinessAccessHelper.class);
        when(access.requireBusiness(7L)).thenReturn(b);
        AccountRepository accountRepo = mock(AccountRepository.class);
        when(accountRepo.findByBusinessOrderByAccountCodeAsc(b)).thenReturn(List.of(
                account(b, 3, "1200", AccountType.ASSET, AccountPurpose.INVENTORY),
                account(b, 4, "4000", AccountType.REVENUE, AccountPurpose.SALES_REVENUE)));
        JournalRepository journalRepo = mock(JournalRepository.class);
        when(journalRepo.findFirstByBusinessAndReferenceStartingWithOrderByIdAsc(any(), any())).thenReturn(Optional.empty());
        JournalService journals = mock(JournalService.class);
        ImportService service = new ImportService(List.of(
                new AccountImporter(access, accountRepo, journalRepo, mock(AccountService.class), journals)));

        ImportResult r = service.run(7L, ImportKind.ACCOUNTS, xlsx(
                new Object[]{"Account code", "Account name", "Opening balance"},
                new Object[]{"1200", "Stock", "900"},
                new Object[]{"4000", "Sales", "100"}), false, "me@x.com");

        assertThat(r.getErrors()).extracting(ImportResult.RowError::row).containsExactly(2, 3);
        verify(journals, never()).post(any(), anyList(), any());
    }
}
