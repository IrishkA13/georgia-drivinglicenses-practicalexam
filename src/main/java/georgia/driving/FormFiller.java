package georgia.driving;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

@SpringBootApplication
public class FormFiller implements CommandLineRunner {

    private static final String URL = "https://my.sa.gov.ge/drivinglicenses/practicalexam";

    public static void main(String[] args) {
        SpringApplication.run(FormFiller.class, args);
    }

    public void run(String[] args) {
        String propertiesPath = args.length > 0 ? args[0] : "input.properties";

        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(propertiesPath);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read file input.properties", e);
        }
        run(
                properties.getProperty("personalNumber"),
                properties.getProperty("phoneNumber"),
                properties.getProperty("category"),
                properties.getProperty("city"),
                properties.getProperty("minDateToSelect"),
                properties.getProperty("maxDateToSelect")
        );
    }

    private static void run(
            String personalNumber,
            String phoneNumber,
            String category,
            String city,
            String minDateToSelect,
            String maxDateToSelect
    ) {
        WebDriverManager.chromedriver().setup();
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

        try {
            driver.get(URL);

            agreeWithRules(driver, wait);

            fillPersonalDetails(driver, wait, personalNumber, phoneNumber, category);

            fillCityDateAvailableTimeAndClickConfirmButton(driver, wait, city, minDateToSelect, maxDateToSelect);

            clickPayButton(driver, wait);

            clickTbcBankLogo(driver, wait);

            logTransId(driver, wait);

            System.out.println("Форма успешно заполнена!");
        } catch (Exception e) {
            System.out.println("Что-то пошло не так" + e);
        } finally {
            // driver.quit(); // оставляем открытым для проверки
        }
    }

    private static void agreeWithRules(WebDriver driver, WebDriverWait wait) {
        int maxAttempts = 5; // максимальное количество попыток
        int attempt = 0;
        boolean clicked = false;

        while (attempt < maxAttempts && !clicked) {
            attempt++;
            try {
                // Ждем видимости контейнера с условиями
                WebElement scrollableDiv = wait.until(ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector("div[appcustomscroll]")));

                // Скроллим до конца через JS
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollTop = arguments[0].scrollHeight;", scrollableDiv);

                // Ждем видимости кнопки
                WebElement agreeButton = wait.until(ExpectedConditions.visibilityOfElementLocated(
                        By.xpath("//button[.//span[text()='ვეთანხმები']]")));

                // Кликаем через JS
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", agreeButton);

                // Ждем, пока overlay исчезнет
                wait.until(ExpectedConditions.invisibilityOfElementLocated(
                        By.cssSelector("div.cdk-overlay-backdrop.cdk-overlay-backdrop-showing")));

                // Если overlay исчез — клик успешен
                clicked = true;
                System.out.println("Кнопка 'ვეთანხმები' успешно нажата!");
            } catch (Exception e) {
                System.out.println("Попытка " + attempt + " не удалась: " + e.getMessage());
            }
        }

        if (!clicked) {
            throw new RuntimeException("Не удалось нажать кнопку согласия после " + maxAttempts + " попыток.");
        }
    }

    private static void fillPersonalDetails(WebDriver driver, WebDriverWait wait, String personalNumber, String phoneNumber, String category) {
        // 1) personalNumber
        WebElement personalNumberField = wait.until(ExpectedConditions.elementToBeClickable(By.name("personalNumber")));
        personalNumberField.clear();
        personalNumberField.sendKeys(personalNumber);

        // нажимаем кнопку поиска
        clickSearchButton(driver, wait);

        // 2) phoneNumber
        WebElement phoneNumberField = wait.until(ExpectedConditions.elementToBeClickable(By.name("phoneNumber")));
        phoneNumberField.clear();
        phoneNumberField.sendKeys(phoneNumber);

        // 3) category
        WebElement categorySelect = wait.until(ExpectedConditions.elementToBeClickable(By.id("mat-select-value-1")));
        categorySelect.click();
        WebElement categoryOption = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//span[contains(text(),'" + category + "')]")));
        categoryOption.click();
    }

    // Надежный клик по кнопке поиска
    private static void clickSearchButton(WebDriver driver, WebDriverWait wait) {
        try {
            // Ждем пока overlay исчезнет
            wait.until(ExpectedConditions.invisibilityOfElementLocated(
                    By.cssSelector("div.cdk-overlay-backdrop.cdk-overlay-dark-backdrop.cdk-overlay-backdrop-showing")));

            WebElement searchButton = wait.until(
                    ExpectedConditions.elementToBeClickable(
                            By.cssSelector("img[src='assets/svg_icons/search48.svg']")));

            // Скроллим к элементу и кликаем через JS
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", searchButton);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", searchButton);

            System.out.println("Кнопка поиска (лупа) нажата!");
        } catch (Exception e) {
            System.out.println("Не удалось нажать кнопку поиска: " + e.getMessage());
        }
    }

    private static void fillCityDateAvailableTimeAndClickConfirmButton(
            WebDriver driver, WebDriverWait wait, String city,
            String minDateToSelect, String maxDateToSelect
    ) {

        boolean timeSelected = false;

        while (!timeSelected) {
            // ---------- 1) Выбор города ----------
            WebElement serviceCenterSelect = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//mat-select[@name='serviceCenterId']")));
            safeClick(driver, wait, serviceCenterSelect);

            WebElement cityOption = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//mat-option//span[contains(text(),'" + city + "')]")));
            safeClick(driver, wait, cityOption);
            System.out.println("Выбран город: " + city);

            // ---------- 2) Выбор даты ----------
            boolean dateSelected = chooseDateInRange(driver, wait, minDateToSelect, maxDateToSelect);
            if (!dateSelected) {
                System.out.println("Нет доступных дат в диапазоне " + minDateToSelect + " - " + maxDateToSelect + ". Пробуем заново...");
                continue; // пробуем заново
            }

            // ---------- 3) Выбор первого доступного времени ----------
            timeSelected = tryChooseFirstAvailableTime(driver, wait);
            if (!timeSelected) {
                System.out.println("Нет доступного времени, пробуем заново...");
            }
        }
    }

    private static void safeClick(WebDriver driver, WebDriverWait wait, WebElement element) {
        try {
            // Ждем исчезновения overlay
            wait.until(ExpectedConditions.invisibilityOfElementLocated(
                    By.cssSelector("div.cdk-overlay-backdrop.cdk-overlay-backdrop-showing")));

            // Скроллим в центр
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);

            // JS-клик
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);

            Thread.sleep(200); // короткая пауза для стабильности
        } catch (Exception e) {
            throw new RuntimeException("Не удалось кликнуть по элементу: " + e.getMessage(), e);
        }
    }

    public static boolean chooseDateInRange(WebDriver driver, WebDriverWait wait, String minDateToSelect, String maxDateToSelect) {
        try {
            // Ждем, пока overlay предыдущего mat-select исчезнет
            wait.until(ExpectedConditions.invisibilityOfElementLocated(
                    By.cssSelector("div.cdk-overlay-backdrop.cdk-overlay-backdrop-showing")));

            WebElement selectDate = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("mat-select-4")));
            safeClick(driver, wait, selectDate);

            WebElement datePanel;

            // Ждем появления хотя бы одной даты
            try {
                datePanel = wait.until(ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector("div.mat-select-panel.mat-primary[aria-labelledby^='mat-form-field-label-']")));

                wait.until(driver1 -> {
                    List<WebElement> options = datePanel.findElements(By.cssSelector("mat-option"));
                    return options.stream().anyMatch(opt -> opt.getText().trim().matches("\\d{2}-\\d{2}-\\d{4}"));
                });
            } catch (TimeoutException exception) {
                System.out.println("Свободных дат нет");
                return false;
            }

            List<WebElement> dateOptions = datePanel.findElements(By.cssSelector("mat-option"));
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
            java.time.LocalDate minDate = java.time.LocalDate.parse(minDateToSelect, formatter);
            java.time.LocalDate maxDate = java.time.LocalDate.parse(maxDateToSelect, formatter);

            for (WebElement option : dateOptions) {
                String text = option.getText().trim().replaceAll("\\s+", "");
                if (!text.equals("გასუფთავება") && text.matches("\\d{2}-\\d{2}-\\d{4}")) {
                    java.time.LocalDate date = java.time.LocalDate.parse(text, formatter);
                    if (!date.isBefore(minDate) && !date.isAfter(maxDate)) {
                        safeClick(driver, wait, option);
                        System.out.println("Выбрана дата: " + text);
                        return true;
                    }
                }
            }

            System.out.println("Нет дат в диапазоне " + minDate + "-" + maxDate);
            return false; // нет дат в диапазоне
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean tryChooseFirstAvailableTime(WebDriver driver, WebDriverWait wait) {
        try {
            // 1. Открываем дропдаун времени
            WebElement selectTime = wait.until(ExpectedConditions.elementToBeClickable(By.id("mat-select-6")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", selectTime);
            selectTime.click();

            // 2. Ждём появления панели с опциями
            WebElement timePanel = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("mat-select-6-panel")));

            // 3. Ждём появления всех опций (кроме "გასუფთავება")
            wait.until(d -> {
                List<WebElement> options = driver.findElements(By.xpath(
                        "//div[@id='mat-select-6-panel']//span[contains(@class,'mat-option-text') and normalize-space(text())!='გასუფთავება']"));
                return options.size() > 0;
            });

            List<WebElement> timeOptions = driver.findElements(By.xpath(
                    "//div[@id='mat-select-6-panel']//span[contains(@class,'mat-option-text') and normalize-space(text())!='გასუფთავება']"));

            if (timeOptions.isEmpty()) {
                return false;
            }

            // 4. Перебираем все доступные времена
            for (int i = 0; i < timeOptions.size(); i++) {
                WebElement option = timeOptions.get(i);
                String timeText = option.getText().trim();

                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true); arguments[0].click();", option);
                System.out.println("Пробуем время: " + timeText);

                clickConfirmButton(driver, wait);

                if (isInvoiceBlock(driver, wait)) {
                    System.out.println("✅ Выбрано время: " + timeText);
                    return true; // нашли рабочее время
                } else {
                    System.out.println("❌ Время " + timeText + " недоступно, пробуем следующее...");
                    // нужно снова открыть дропдаун, чтобы выбрать следующее
                    selectTime = wait.until(ExpectedConditions.elementToBeClickable(By.id("mat-select-6")));
                    selectTime.click();

                    // обновляем список опций
                    timeOptions = driver.findElements(By.xpath(
                            "//div[@id='mat-select-6-panel']//span[contains(@class,'mat-option-text') and normalize-space(text())!='გასუფთავება']"));
                }
            }

            // если дошли сюда — все опции перебрали
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static void clickConfirmButton(final WebDriver driver, final WebDriverWait wait) {
        // 7) финальная кнопка
        WebElement finalButton = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[.//span[text()='დაჯავშნა']]")));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true); arguments[0].click();", finalButton);
    }

    private static boolean isInvoiceBlock(WebDriver driver, WebDriverWait wait) {
        try {
            By invoiceBlockLocator = By.xpath("//div[contains(@class,'innerTable')]//span[text()='ინვოისი']");

            // Ждем, пока элемент с текстом "ინვოისი" станет видимым
            wait.until(ExpectedConditions.visibilityOfElementLocated(invoiceBlockLocator));
            return true;
        } catch (TimeoutException e) {
            return false; // сообщение не появилось
        }
    }

    public static void clickPayButton(WebDriver driver, WebDriverWait wait) {
        By payButtonLocator = By.xpath("//span[contains(@class,'mat-button-wrapper') and normalize-space(text())='გადახდა']");

        WebElement payButton = wait.until(ExpectedConditions.elementToBeClickable(payButtonLocator));
        payButton.click();
    }

    private static void clickTbcBankLogo(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement tbcLogo = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("div.description img.logoimg[src*='tbc_bank.png']")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", tbcLogo);
            tbcLogo.click();
            System.out.println("Клик по логотипу TBC Bank выполнен");
        } catch (TimeoutException e) {
            throw new RuntimeException("Не удалось найти логотип TBC Bank на странице", e);
        }
    }

    private static void logTransId(WebDriver driver, WebDriverWait wait) {
        try {
            // Ждем, пока input с trans_id станет видимым/доступным
            WebElement transIdInput = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("form#cardentry input[name='trans_id']"))
            );

            String transId = transIdInput.getAttribute("value");
            System.out.println("Trans ID: https://ecommerce.ufc.ge/ecomm2/ClientHandler?trans_id=" + transId);
        } catch (Exception e) {
            System.out.println("Не удалось получить transID");
        }
    }
}
