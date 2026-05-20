package com.ausiankou.ordersservice;

import com.ausiankou.dto.CreateOrderRequest;
import com.ausiankou.dto.OrderItemRequest;
import com.ausiankou.dto.OrderResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@AutoConfigureWireMock(port = 8081)
@Testcontainers
class OrderServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("user-service.url", () -> "http://localhost:8081");
    }

    @BeforeEach
    void setUp() {
        WireMock.reset();
    }

    @Test
    void createOrder_WithValidData_ReturnsCreatedOrder() throws Exception {
        // Mock user service response
        stubFor(get(urlEqualTo("/api/users/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                    {
                        "id": 1,
                        "email": "test@example.com",
                        "firstName": "Test",
                        "lastName": "User"
                    }
                """)));

        // First create an item
        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                        "name": "Test Product",
                        "price": 99.99
                    }
                """))
                .andExpect(status().isCreated());

        // Create order request
        CreateOrderRequest request = CreateOrderRequest.builder()
                .userId(1L)
                .items(List.of(
                        OrderItemRequest.builder()
                                .itemId(1L)
                                .quantity(2)
                                .build()
                ))
                .build();

        // Create order
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.userId", is(1)))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.totalPrice", is(199.98)))
                .andExpect(jsonPath("$.userInfo.email", is("test@example.com")));

        verify(getRequestedFor(urlEqualTo("/api/users/1")));
    }

    @Test
    void createOrder_WithInvalidData_ReturnsBadRequest() throws Exception {
        CreateOrderRequest invalidRequest = CreateOrderRequest.builder()
                .userId(null)
                .items(List.of())
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")));
    }

    @Test
    void getOrderById_WhenOrderExists_ReturnsOrder() throws Exception {
        // Mock user service
        stubFor(get(urlEqualTo("/api/users/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("""
                    {
                        "id": 1,
                        "email": "test@example.com",
                        "firstName": "Test",
                        "lastName": "User"
                    }
                """)));

        // Create an order first
        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                        "name": "Test Product",
                        "price": 49.99
                    }
                """))
                .andExpect(status().isCreated());

        CreateOrderRequest request = CreateOrderRequest.builder()
                .userId(1L)
                .items(List.of(
                        OrderItemRequest.builder()
                                .itemId(1L)
                                .quantity(1)
                                .build()
                ))
                .build();

        String response = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        OrderResponse createdOrder = objectMapper.readValue(response, OrderResponse.class);

        // Get order by id
        mockMvc.perform(get("/api/orders/{id}", createdOrder.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(createdOrder.getId().intValue())))
                .andExpect(jsonPath("$.userInfo.email", is("test@example.com")));
    }

    @Test
    void getOrderById_WhenOrderNotFound_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/orders/{id}", 99999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Order not found")));
    }

    @Test
    void getOrdersWithFilters_ReturnsFilteredOrders() throws Exception {
        // Mock user service for multiple users
        stubFor(get(urlMatching("/api/users/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("""
                    {
                        "id": 1,
                        "email": "test@example.com",
                        "firstName": "Test",
                        "lastName": "User"
                    }
                """)));

        // Create test data
        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                        "name": "Product 1",
                        "price": 10.00
                    }
                """))
                .andExpect(status().isCreated());

        CreateOrderRequest request = CreateOrderRequest.builder()
                .userId(1L)
                .items(List.of(
                        OrderItemRequest.builder()
                                .itemId(1L)
                                .quantity(1)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Get orders with filters
        mockMvc.perform(get("/api/orders")
                        .param("statuses", "PENDING")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.content[0].status", is("PENDING")));
    }

    @Test
    void updateOrder_WithValidData_ReturnsUpdatedOrder() throws Exception {
        // Mock user service
        stubFor(get(urlEqualTo("/api/users/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("""
                    {
                        "id": 1,
                        "email": "test@example.com",
                        "firstName": "Test",
                        "lastName": "User"
                    }
                """)));

        // Create order
        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                        "name": "Product",
                        "price": 25.00
                    }
                """))
                .andExpect(status().isCreated());

        CreateOrderRequest createRequest = CreateOrderRequest.builder()
                .userId(1L)
                .items(List.of(
                        OrderItemRequest.builder()
                                .itemId(1L)
                                .quantity(1)
                                .build()
                ))
                .build();

        String response = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        OrderResponse createdOrder = objectMapper.readValue(response, OrderResponse.class);

        // Update order
        String updateRequest = """
            {
                "status": "CONFIRMED",
                "items": [
                    {
                        "itemId": 1,
                        "quantity": 3
                    }
                ]
            }
            """;

        mockMvc.perform(put("/api/orders/{id}", createdOrder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));
    }

    @Test
    void deleteOrder_SoftDelete_Success() throws Exception {
        // Create order first
        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                        "name": "Product to Delete",
                        "price": 100.00
                    }
                """))
                .andExpect(status().isCreated());

        CreateOrderRequest request = CreateOrderRequest.builder()
                .userId(1L)
                .items(List.of(
                        OrderItemRequest.builder()
                                .itemId(1L)
                                .quantity(1)
                                .build()
                ))
                .build();

        String response = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        OrderResponse createdOrder = objectMapper.readValue(response, OrderResponse.class);

        // Delete order
        mockMvc.perform(delete("/api/orders/{id}", createdOrder.getId()))
                .andExpect(status().isNoContent());

        // Verify order is soft deleted (should not be found)
        mockMvc.perform(get("/api/orders/{id}", createdOrder.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUserInfo_WhenUserServiceUnavailable_UsesCircuitBreaker() throws Exception {
        // Simulate user service failure
        stubFor(get(urlEqualTo("/api/users/1"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));

        // Create order
        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                        "name": "Test Product",
                        "price": 50.00
                    }
                """))
                .andExpect(status().isCreated());

        CreateOrderRequest request = CreateOrderRequest.builder()
                .userId(1L)
                .items(List.of(
                        OrderItemRequest.builder()
                                .itemId(1L)
                                .quantity(1)
                                .build()
                ))
                .build();

        // Order should still be created with fallback user info
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userInfo.id", is(1)))
                .andExpect(jsonPath("$.userInfo.email", is("unknown@example.com")))
                .andExpect(jsonPath("$.userInfo.firstName", is("Unknown")));
    }
}
