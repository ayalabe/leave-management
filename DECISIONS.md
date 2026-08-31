# DECISIONS

## 1. החלטות ארכיטקטוניות

**פיצול Controller לשכבות:**
הוצאתי `LeaveRequestService` שמכיל את כל הלוגיקה העסקית — בדיקת מכסה, יצירת בקשה, ואישור. ה-Controller נשאר רק עם HTTP mapping (קבלת request, החזרת response). הבחירה ב-Service ולא ב-Repository pattern נוספת — כי ה-Repository כבר מסופק על ידי Spring Data, ושכבת Service מספיקה לפרויקט בגודל הזה.

**Frontend:**
הוצאתי `LeaveRequestsService` שמרכז את כל קריאות ה-HTTP. הקומפוננטה עוברת לשימוש בטיפוסים אמיתיים (`Employee`, `LeaveRequest`) במקום `any`, ו-`takeUntil(destroy$)` למניעת memory leaks.

---

## 2. הבאג ביתרת החופשה

**מה היה הבאג:** ב-`LeaveRequestsController.create` (שורה 79 במקור), הבדיקה הייתה:
```java
if (dto.getType() == LeaveType.VACATION && days > employee.getAnnualQuota())
```
המשתנה `used` (ימים שכבר נוצלו) חושב אבל מעולם לא נוסף לבדיקה — כך עובד עם 18 ימים מאושרים מתוך מכסה 20 יכול לבקש עוד 15 ימים בלי בעיה.

**התיקון:**
```java
if (dto.getType() == LeaveType.VACATION && (used + days) > employee.getAnnualQuota())
```

**הטסט שמוכיח:**
`create_ExceedsQuotaWhenCombinedWithApproved_Returns400` — יוצר עובד עם מכסה 20, שומר 18 ימים מאושרים ישירות ב-DB, מנסה לבקש עוד 5 ימים, ומוודא שחוזר 400.

---

## 3. אישור בקשה (approve) ו-concurrency

**מצבים לא חוקיים:**
- בקשה לא קיימת → 404
- בקשה שסטטוס שלה אינו PENDING → 409 עם הסבר

**מקביליות:**
השתמשתי ב-`PESSIMISTIC_WRITE` lock דרך `findByIdWithLock` ב-Repository. כשמנהל מאשר בקשה, ה-DB נועל את השורה — מנהל שני שינסה לאשר בו-זמנית יחכה עד שהטרנזקציה הראשונה תסתיים. בנוסף, לאחר הנעילה בודקים מחדש את מצב המכסה — כך אם שתי בקשות של אותו עובד מאושרות במקביל, השניה תיכשל אם תחרוג מהמכסה.

**מה הייתי מוסיף עם עוד זמן:**
Optimistic locking עם `@Version` כחלופה קלה יותר לסביבות עם קונפליקטים נדירים.

---

## 4. על מה ויתרתי בגלל הזמן

- **טסטים ל-approve endpoint** — אין טסט אינטגרציה שבודק את ה-409 ואת בדיקת המכסה באישור. הייתי מוסיף.
- **Pagination** — הרשימה מחזירה את כל הבקשות. בסביבת ייצור צריך paging.
- **Response DTOs** — הישויות מוחזרות ישירות מה-controller. הייתי יוצר DTOs נפרדים כדי לא לחשוף את מבנה ה-DB.
- **Error handling גלובלי** — הייתי מוסיף `@ControllerAdvice` לטיפול אחיד בשגיאות.

---

## 5. שימוש ב-AI

### איפה AI עזר

1. **Prompt:** "הסבר לי מה הבאג ב-LeaveRequestsController.create ואיך לתקן אותו"
   **מה קיבלתי:** הסבר מדויק על שורה 79 — ש-`used` מחושב אבל לא מוסף לבדיקה. AI זיהה את הבאג מיידית.

2. **Prompt:** "כתוב endpoint לאישור בקשה עם pessimistic locking ב-Spring Data JPA"
   **מה קיבלתי:** קיבלתי את השימוש ב-`@Lock(LockModeType.PESSIMISTIC_WRITE)` עם `@Query` — שילבתי אותו ב-Repository.

3. **Prompt:** "צור Angular reactive form עם validator שבודק שתאריך התחלה לפני תאריך סיום"
   **מה קיבלתי:** את `dateRangeValidator` — custom validator על ה-FormGroup כולו, לא על שדה בודד.

### איפה דחיתי/תיקנתי הצעה של AI

AI הציע להשתמש ב-`@Transactional` על ה-Controller ישירות לצורך הנעילה. דחיתי — Transactions שייכות לשכבת ה-Service, לא ל-Controller. העברתי את ה-`@Transactional` ל-`LeaveRequestService.approve`.

### אבטחה

**SQL Injection** ב-`/search` endpoint (קובץ `LeaveRequestsController.java`, שורה 51 במקור):
```java
String sql = "... WHERE name LIKE '%" + name + "%'";
```
קלט ישיר מהמשתמש משורשר לתוך ה-SQL — תוקף יכול להזריק קוד SQL דרך פרמטר ה-name.

**התיקון:**
```java
.createNativeQuery(sql, LeaveRequest.class)
.setParameter("pattern", "%" + name + "%")
```
שימוש ב-named parameter — ה-DB מטפל ב-escaping.

---

## 6. הוראות הרצה

הפרויקט הורץ ללא Docker (בשל מגבלות מחשב). במקום:
- PostgreSQL מותקן מקומית, DB בשם `leave`, משתמש `leave`
- Backend: `java -jar target/leave-management-1.0.0.jar` מתיקיית `backend`
- Frontend: `npm start` מתיקיית `frontend`
