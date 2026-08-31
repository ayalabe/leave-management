# DECISIONS

## 1. החלטות ארכיטקטוניות

**פיצול Controller לשכבות:**
הוצאתי `LeaveRequestService` שמכיל את כל הלוגיקה העסקית - בדיקת מכסה, יצירת בקשה, ואישור.
ה-Controller נשאר רק עם HTTP mapping (קבלת request, החזרת response).
הבחירה ב-Service ולא בשכבת Repository נוספת - כי Spring Data כבר מספק את ה-Repository, ושכבת Service מספיקה לפרויקט בגודל הזה.

**Frontend:**
הוצאתי `LeaveRequestsService` שמרכז את כל קריאות ה-HTTP.
הקומפוננטה עוברת לשימוש בטיפוסים אמיתיים (`Employee`, `LeaveRequest`) במקום `any`,
ו-`takeUntil(destroy$)` למניעת memory leaks.

---

## 2. הבאג ביתרת החופשה

**מה היה הבאג, איפה:**
ב-`LeaveRequestsController.create` שורה 79 - הבדיקה השוותה רק את ימי הבקשה החדשה מול המכסה, בלי לחבר ימים שכבר נוצלו:
```java
if (dto.getType() == LeaveType.VACATION && days > employee.getAnnualQuota())
```
המשתנה `used` חושב אבל לא שומש בבדיקה.

**איך תיקנתי:**
```java
if (dto.getType() == LeaveType.VACATION && (used + days) > employee.getAnnualQuota())
```

**הטסט שמוכיח את התיקון:**
`create_ExceedsQuotaWhenCombinedWithApproved_Returns400` -
יוצר עובד עם מכסה 20, שומר 18 ימים מאושרים ישירות ב-DB,
מנסה לבקש עוד 5 ימים, ומוודא שחוזר 400.

---

## 3. אישור בקשה (approve) ו-concurrency

**איך טיפלתי במצבים לא חוקיים:**
- בקשה לא קיימת - 404
- בקשה שסטטוס שלה אינו PENDING - 409 עם הסבר

**מה לגבי אישור של שתי בקשות במקביל:**
השתמשתי ב-`PESSIMISTIC_WRITE` lock דרך `findByIdWithLock` ב-Repository.
כשמנהל מאשר בקשה, ה-DB נועל את השורה - מנהל שני שינסה לאשר בו-זמנית יחכה עד שהטרנזקציה הראשונה תסתיים.
לאחר הנעילה בודקים מחדש את מצב המכסה - אם שתי בקשות של אותו עובד מאושרות במקביל, השניה תיכשל אם תחרוג מהמכסה.

---

## 4. על מה ויתרתי בגלל הזמן

- **טסטים ל-approve endpoint** - אין טסט אינטגרציה שבודק את ה-409 ואת בדיקת המכסה באישור.
- **Pagination** - הרשימה מחזירה את כל הבקשות. בסביבת ייצור צריך paging.
- **Response DTOs** - הישויות מוחזרות ישירות מה-controller. הייתי יוצר DTOs נפרדים לחשיפת API נקייה.
- **Error handling גלובלי** - הייתי מוסיף `@ControllerAdvice` לטיפול אחיד בשגיאות.

---

## 5. שימוש ב-AI

### איפה AI עזר (כולל prompts)

1. **prompt:** "יש לי approve endpoint שצריך לטפל במקביליות - שני מנהלים יכולים לאשר שתי בקשות של אותו עובד בו-זמנית ויחד לחרוג מהמכסה. חשבתי על pessimistic locking ב-JPA - האם זה הכיוון הנכון לתרחיש כזה, או שoptimistic locking יספיק?"
   AI אישר שpessimistic locking מתאים כאן כי הבקשות נדירות אך הנזק מחריגת מכסה גבוה. זה חיזק את ההחלטה שכבר הגעתי אליה.

2. **prompt:** "כתבתי service layer שמפריד לוגיקה עסקית מה-Controller. עברתי על הקוד ושמתי לב שה-search endpoint עדיין בונה SQL עם שרשור מחרוזות - האם זו SQL Injection גם אם הUI מסנן קלט?"
   AI אישר שזו SQL Injection קלאסית והדגיש שאסור לסמוך על סינון בצד הלקוח. תיקנתי עם named parameter.

3. **prompt:** "אני רוצה validation בטופס Angular שיבדוק שתאריך התחלה לא מאוחר מתאריך סיום. validator על שדה בודד לא יעבוד כאן כי צריך גישה לשני שדות - האם נכון לשים validator על ה-FormGroup כולו?"
   AI אישר שהגישה נכונה וסיפק את התחביר של AbstractControl. כתבתי את `dateRangeValidator` בעצמי.

### דוגמה אחת שבה דחיתי/תיקנתי הצעה של AI

AI הציע לשים `@Transactional` ישירות על method ב-Controller כדי להבטיח שהנעילה תעבוד.
דחיתי - Controller לא אמור להכיל transaction management, זה שייך לשכבת ה-Service.
קבלת ההצעה היתה פוגעת בארכיטקטורה שבניתי. העברתי את ה-`@Transactional` ל-`LeaveRequestService.approve`.

### אבטחה

**בעיה שמצאתי:** SQL Injection ב-`/search` endpoint (`LeaveRequestsController.java` שורה 51):
```java
String sql = "... WHERE name LIKE '%" + name + "%'";
```
קלט מהמשתמש משורשר ישירות ל-SQL - תוקף יכול להזריק קוד.

**התיקון:**
```java
.createNativeQuery(sql, LeaveRequest.class)
.setParameter("pattern", "%" + name + "%")
```
שימוש ב-named parameter - ה-DB מטפל ב-escaping.

---

## 6. הוראות הרצה

לפי הוראות ה-README המקוריות - `docker compose up --build` מריץ את הכל.
