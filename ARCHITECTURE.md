# ProTaxo ERP – Технічна специфікація та контекст проєкту

## 1. Опис проєкту
ProTaxo ERP – це спеціалізована веб-система для управління тахосервісом (калібрування та обслуговування тахографів для вантажних автомобілів). Система забезпечує ведення клієнтської бази, автопарку, складу, створення нарядів-замовлень, генерацію документів (рахунки, акти, протоколи калібрування) та інтеграцію з локальним термопринтером для друку наклейок.

## 2. Технологічний стек
- **Backend:** Java 21, Spring Boot 3.x (Web, Data JPA, Security).
- **База даних:** PostgreSQL.
- **Міграції бази даних:** Flyway (або Liquibase).
- **Аудит та історія змін:** Hibernate Envers (система не підтримує фізичного видалення важливих даних, лише логічне або фіксацію змін).
- **Безпека:** Spring Security + JWT, Role-Based Access Control (RBAC).
- **Генерація PDF:** Thymeleaf + Flying Saucer / OpenPDF (генерація рахунків, актів, PDF-протоколів).
- **Frontend (на майбутнє):** React (Next.js або Vite) + TypeScript.
- **Локальний друк (Print Agent):** Фоновий сервіс на комп'ютері СТО, який через WebSocket отримує команди від бекенду та відправляє TSPL/ZPL команди на USB-термопринтер.

## 3. Основна бізнес-логіка
### 3.1. Життєвий цикл Замовлення-Наряду (Order)
1. **Чернетка (Draft):** Можна редагувати все, наряд ще не має офіційного номера.
2. **В роботі (In Progress):** Присвоюється постійний номер (напр., №000154). Видалити неможливо. Можна змінювати авто, послуги, матеріали.
3. **Документи сформовані (Docs Formed):** Сформовано рахунок/акт. Заборонено видаляти позиції чи змінювати ціни/клієнта/автомобіль. Дозволено лише додавати нові роботи/матеріали (допродаж).
4. **Завершено (Completed):** Оплачено, протокол підписано, наклейку роздруковано. Будь-які зміни можливі лише адміністратором із записом у лог.
5. **Архів (Archived).**

### 3.2. Робочий процес створення наряду
- Вибір Клієнта -> автоматично підтягується Договір.
- Вибір Автомобіля -> автоматично підтягується VIN, Тахограф, останні W/K/L, Водій.
- Вибір Майстра, Причини калібрування.
- Додавання робіт та матеріалів (матеріали списуються зі складу).
- Розрахунок вартості.

## 4. Структура Бази Даних (Основні Сутності / Entities)

**4.1. Users & Security (Користувачі та Права)**
- `User`: id, fullName, email, passwordHash, roleId, isActive.
- `Role_Permissions`: roleName, permissions (JSON з детальними правами: can_create_order, can_edit_completed тощо).

**4.2. Clients & Fleet (Клієнти та Автопарк)**
- `Client`: id, name, edrpou, contacts, defaultPaymentType.
- `Contract`: id, clientId, contractNumber, status.
- `Vehicle`: id, clientId, vin, registrationNumber, make, model, year.
- `Driver`: id, clientId, fullName, phone.

**4.3. Equipment (Обладнання)**
- `Tachograph`: id, vehicleId, manufacturer, model, version, serialNumber, productionDate.

**4.4. Warehouse & Services (Каталог і Склад)**
- `Catalog_Item`: id, type (SERVICE/MATERIAL), name, basePrice, stockQuantity.

**4.5. Orders (Наряди)**
- `Order`: id, orderNumber, status, clientId, contractId, vehicleId, driverId, masterId, calibrationReason, sumServices, sumMaterials, totalSum, paymentType, paymentStatus.
- `OrderItem`: id, orderId, catalogItemId, nameAtSaleTime, quantity, price, sum.

**4.6. Calibration (Калібрування)**
- `CalibrationProtocol`: id, orderId, vehicleId, tachographId, calibrationDate, nextCheckDate, w, k, l, tyreSize, sealNumbers, qrHash.

**4.7. Audit (Аудит)**
- `AuditLog` (генерується Hibernate Envers): фіксує хто, коли і яке поле змінив.

## 5. Інструкції для LLM (Асистента)
При генерації коду для цього проєкту, дотримуйся наступних правил:
1. Використовуй сучасні можливості Java 21 (Records, Pattern Matching, тощо).
2. Завжди використовуй анотації Lombok (`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`, `@Slf4j`) для зменшення шаблонного коду.
3. Всі Entity класи повинні мати правильні зв'язки JPA (`@OneToMany`, `@ManyToOne`) та налаштування лінивого завантаження (`FetchType.LAZY`).
4. Не використовуй фізичне видалення (`repository.delete()`) для сутностей нарядів, документів чи історії автомобіля — використовуй логічне видалення (soft delete) або заборону видалення.
5. Розділяй логіку по шарах: Controller -> Service -> Repository. Контролери приймають та повертають DTO, а не самі Entity. Для мапінгу використовуй MapStruct або ручні мапери. 