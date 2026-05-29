# OrDexxa API Gateway

API Gateway del MVP de OrDexxa.

## Objetivo

El usuario o frontend no debe consumir directamente la API del backend.

Toda petición externa debe pasar primero por el API Gateway, que enruta hacia los servicios internos de OrDexxa.

## Tecnologías

- Java 21
- Spring Boot 3.5.14
- Spring Cloud Gateway
- WebFlux
- Maven

## Puertos

- API Gateway: 8080
- Backend OrDexxa: 8081

## Ruta configurada

El gateway recibe:

POST http://localhost:8080/api/proveedores

Y enruta hacia el backend interno:

POST http://localhost:8081/api/proveedores

## Configuración principal

spring.application.name=ordexxa-api-gateway

server.port=8080

spring.cloud.gateway.server.webflux.routes[0].id=ordexxa-provider-route
spring.cloud.gateway.server.webflux.routes[0].uri=http://localhost:8081
spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/api/proveedores/**

## Flujo de consumo

Cliente / Postman / Frontend
        |
        v
API Gateway puerto 8080
        |
        v
Backend OrDexxa puerto 8081
        |
        v
PostgreSQL

## Ejecución

Primero debe estar corriendo el backend en el puerto 8081.

Luego ejecutar el gateway:

./mvnw clean compile

./mvnw spring-boot:run

El gateway corre en:

http://localhost:8080

## Prueba con Postman

Método:

POST

URL:

http://localhost:8080/api/proveedores

Body JSON de ejemplo:

{
  "businessName": "Vape Import Colombia S.A.S.",
  "documentNumber": "900111222-3",
  "email": "compras@vapeimport.com",
  "phoneNumber": "3104567890",
  "address": "Bogotá, Colombia"
}

Respuesta esperada:

201 Created

{
  "id": "uuid-generado",
  "businessName": "Vape Import Colombia S.A.S.",
  "documentNumber": "900111222-3",
  "message": "Proveedor registrado exitosamente"
}
