# Loan Management System

A comprehensive banking solution for managing loan applications, installments, and payments with credit limit controls and role-based access.

## Features

- **Loan Creation**
    - Principal + interest rate calculations
    - Installment scheduling
    - Credit limit validation
    - Admin/user access controls

- **Payment Processing**
    - Installment payment distribution
    - Payment window validation
    - Automatic loan status updates

- **Security**
    - Role-based access (Admin/User)
    - Ownership validation
    - Spring Security integration

- **Credit Management**
    - Dynamic credit limit tracking
    - Automatic limit release on full payment
    - Configurable limits and thresholds

- **Reporting**
    - Loan history by customer
    - Installment tracking
    - Payment receipts

## Tech Stack

- **Core**: Java 17, Spring Boot 3.x
- **Persistence**: JPA/Hibernate, H2 Database (dev)
- **Security**: Spring Security, JWT (example implementation)
- **Tools**: Lombok, SLF4J, ModelMapper
- **Validation**: Custom exception hierarchy

## Installation

1. Clone repository:
   ```bash
   git clone https://github.com/lutfucagatay/loan-api.git
   ```

2. Build with Maven:
   ```bash
   mvn clean install
   ```

3. Configure application.yml:
```yaml
loan:
config:
    installments-allowed: [6, 9, 12, 24]
    interest-min: 0.1
    interest-max: 0.5
    max-allowed-due-month-count: 3
    day-of-payment: 1
```

4. Run the application:
   ```bash
   mvn spring-boot:run
   ```

# Usage

## API Documentation

### Base URL
`http://your-domain.com/loans`

### Authentication
All endpoints require JWT authentication with appropriate role:
- `ADMIN`: Full access
- `USER`: Access to own resources only
- User should encode using Base64 `username:password` and include it in the `Authorization` header
```
  "Authorization": "Basic am9objpwYXNzd29yZA="
```

---

### Endpoints

#### 1. Create Loan
```http
POST /loans
```
**Permissions**:
- Admin: Any customer
- User: Self only

**Request Body**:
```json
{
  "customerId": 123,
  "amount": 10000.00,
  "interestRate": 0.08,
  "installments": 12
}
```

**Success Response**:
```json
{
  "id": 456,
  "loanAmount": 10800.00,
  "numberOfInstallments": 12,
  "createDate": "2023-07-20",
  "paid": false
}
```

---

#### 2. Get Customer Loans
```http
GET /loans/customer/{customerId}
```
**Permissions**:
- Admin: Any customer
- User: Self only

**Response**:
```json
[
  {
    "id": 456,
    "loanAmount": 10800.00,
    "numberOfInstallments": 12,
    "createDate": "2023-07-20",
    "paid": false
  }
]
```

---

#### 3. Get All Loans (Admin Only)
```http
GET /loans
```
**Permissions**: Admin only

**Response**:
```json
[
  {
    "id": 456,
    "loanAmount": 10800.00,
    "numberOfInstallments": 12,
    "createDate": "2023-07-20",
    "paid": false
  }
]
```

---

#### 4. Process Payment
```http
POST /loans/{loanId}/pay?amount={amount}
```
**Permissions**:
- Admin: Any loan
- User: Own loans only

**Response**:
```json
{
  "paidInstallments": 2,
  "totalPaid": 1500.00,
  "remainingFunds": 0.00
}
```

---

#### 5. Get Loan Installments
```http
GET /loans/{loanId}/installments
```
**Permissions**:
- Admin: Any loan
- User: Own loans only

**Response**:
```json
[
  {
    "id": 789,
    "amount": 900.00,
    "dueDate": "2023-08-01",
    "isPaid": false,
    "paidAmount": 0.00
  }
]
```

---

### Error Handling

| Status | Error                        | Description                          |
|--------|------------------------------|--------------------------------------|
| 400    | Invalid Request              | Validation failures                  |
| 401    | Unauthorized                 | Missing/invalid credentials          |
| 403    | Forbidden                    | Insufficient permissions             |
| 404    | Loan Not Found               | Invalid loan ID                      |
| 409    | Credit Limit Exceeded        | Loan exceeds credit capacity         |
| 422    | No Due Installments          | Payment with no payable installments |

---

### Swagger UI Documentation

You can access the Swagger UI documentation at [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

### Data Formats

**Monetary Values**:
- Decimal format with 2 places (e.g., `100.50`)
- Minimum value: `0.00`

**Dates**:
- ISO-8601 format: `YYYY-MM-DD`

**Installment Options**:
- Allowed values: 6, 9, 12, 24 months

**Interest Rates**:
- Range: 10% (0.1) to 50% (0.5)

## Improvement Suggestions

1. Reading user credentials from some secure external source (e.g., a database).
2. Using a different authentication mechanism (e.g., JWT).
3. Implementing more advanced payment processing logic.
4. Adding more error handling and validation.
5. Adding more unit and integration tests.
6. Divide LoanService into smaller classes for better organization.
7. Create testing containers for Docker.
8. Add throttling for API requests.
9. Add caching for frequently accessed data.