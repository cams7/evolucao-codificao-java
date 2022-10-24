package br.com.cams7.test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.modelmapper.ModelMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class ReactorTest1 {

  private static final boolean SHOW_LOGS = true;
  private static final Map<Integer, Boolean> SHOW_TESTS =
      Map.of(1, true, 2, true, 3, true, 4, true, 5, true);

  public static void main(String[] args) {
    final var app = new ReactorTest1();

    if (SHOW_TESTS.get(1)) {
      app.saveOrder(1l)
          .subscribe(
              order -> {
                System.out.println("1. Registred order:");
                System.out.println(order);
              },
              error -> {
                System.out.println("1. Error: " + error.getMessage());
              },
              () -> {
                System.out.println("1. Completed");
              });
    }
    if (SHOW_TESTS.get(2)) {
      app.saveOrder(2l)
          .subscribe(
              order -> {
                System.out.println("2. Registred order with invalid payment:");
                System.out.println(order);
              },
              error -> {
                System.out.println("2. Error: " + error.getMessage());
              },
              () -> {
                System.out.println("2. Completed");
              });
    }
    if (SHOW_TESTS.get(3)) {
      app.saveOrder(5l)
          .subscribe(
              order -> {
                System.out.println("3. Customer not found:");
                System.out.println(order);
              },
              error -> {
                System.out.println("3. Error: " + error.getMessage());
              },
              () -> {
                System.out.println("3. Completed");
              });
    }
    if (SHOW_TESTS.get(4)) {
      app.saveOrder(4l)
          .subscribe(
              order -> {
                System.out.println("4. Customer's card not found:");
                System.out.println(order);
              },
              error -> {
                System.out.println("4. Error: " + error.getMessage());
              },
              () -> {
                System.out.println("4. Completed");
              });
    }
    if (SHOW_TESTS.get(5)) {
      app.saveOrder(3l)
          .subscribe(
              order -> {
                System.out.println("5. Customer cart's items not found:");
                System.out.println(order);
              },
              error -> {
                System.out.println("5. Error: " + error.getMessage());
              },
              () -> {
                System.out.println("5. Completed");
              });
    }
  }

  private static final ModelMapper MODEL_MAPPER = new ModelMapper();

  private static final Map<Long, CustomerResponse> CUSTOMERS =
      List.of(
              new CustomerResponse(1l, "Gael", "Alves"),
              new CustomerResponse(2l, "Edson", "Brito"),
              new CustomerResponse(3l, "Elaine", "Teixeira"),
              new CustomerResponse(4l, "Stella", "Paz"))
          .parallelStream()
          .collect(Collectors.toMap(CustomerResponse::getId, Function.identity()));

  private static Map<Long, CustomerCardResponse> CUSTOMER_CARDS =
      List.of(
              new CustomerCardResponse(1l, "5172563238920845"),
              new CustomerCardResponse(2l, "5585470523496195"),
              new CustomerCardResponse(3l, "4916563711189276"))
          .parallelStream()
          .collect(Collectors.toMap(CustomerCardResponse::getCustomerId, Function.identity()));

  private static Map<Long, List<CartItemResponse>> CART_ITEMS =
      List.of(
              new CartItemResponse(1l, 101l, 1, 25.5),
              new CartItemResponse(1l, 102l, 2, 10.3),
              new CartItemResponse(1l, 103l, 3, 16.8),
              new CartItemResponse(2l, 101l, 2, 25.5),
              new CartItemResponse(2l, 102l, 5, 10.3))
          .parallelStream()
          .collect(Collectors.groupingBy(CartItemResponse::getCustomerId));

  private static Map<Long, Boolean> CUSTOMER_PAYMENTS = Map.of(1l, true, 2l, false);

  private static List<OrderModel> ORDERS = new ArrayList<>();

  // Webclient layer
  private Mono<Customer> getCustomerById(Long customerId) {
    log("1.1. Get customer by id: customerId={}", customerId);
    final var response = CUSTOMERS.get(customerId);
    return Mono.justOrEmpty(response)
        .map(
            customer ->
                new Customer()
                    .withCustomerId(customerId)
                    .withFullName(
                        String.format("%s %s", customer.getFirstName(), customer.getLastName())))
        .doOnNext(customer -> log("1.2. Getting customer: customer={}", customer));
  }

  // Webclient layer
  private Mono<CustomerCard> getCustomerCardByCustomerId(Long customerId) {
    log("2.1. Get customer's card by customer id: customerId={}", customerId);
    final var response = CUSTOMER_CARDS.get(customerId);
    return Mono.justOrEmpty(response)
        .map(card -> MODEL_MAPPER.map(card, CustomerCard.class))
        .doOnNext(card -> log("2.2. Getting customer's card: card={}", card));
  }

  // Webclient layer
  private Flux<CartItem> getCartItemsByCustomerId(Long customerId) {
    log("3.1. Get customer cart's items by customer id: customerId={}", customerId);
    final var response = CART_ITEMS.get(customerId);
    if (CollectionUtils.isEmpty(response)) return Flux.empty();
    return Flux.fromIterable(response)
        .map(
            item ->
                MODEL_MAPPER
                    .map(item, CartItem.class)
                    .withTotalAmount(item.getUnitPrice() * item.getQuantity()))
        .doOnNext(item -> log("3.2. Getting customer cart's item: item={}", item));
  }

  // Webclient layer
  private Mono<Boolean> isValidPaymentByCustomerId(Long customerId) {
    log("5.1. Is valid payment by customer id: customerId={}", customerId);
    return Mono.justOrEmpty(CUSTOMER_PAYMENTS.get(customerId))
        .doOnNext(
            isValidPayment -> log("5.2. Is valid payment: isValidPayment={}", isValidPayment));
  }

  // Repository layer
  private Mono<OrderEntity> saveOrder(OrderEntity order) {
    log("4.1. Save order: order={}", order);
    final var customer = MODEL_MAPPER.map(order.getCustomer(), CustomerModel.class);
    final var card = MODEL_MAPPER.map(order.getCard(), CustomerCardModel.class);
    final var items =
        order.getItems().parallelStream()
            .map(item -> MODEL_MAPPER.map(item, CartItemModel.class))
            .collect(Collectors.toList());
    final var model = new OrderModel();
    model.setId(UUID.randomUUID().toString());
    model.setRegistrationDate(order.getRegistrationDate().toLocalDateTime());
    model.setTotal(order.getTotalAmount());
    model.setValidPayment(order.getValidPayment());
    model.setCustomer(customer);
    model.setCard(card);
    model.setItems(items);
    ORDERS.add(model);
    return Mono.just(getOrder(model))
        .doOnNext(savedOrder -> log("4.2. Saving order: order={}", savedOrder));
  }

  // Repository layer
  private Mono<OrderEntity> updatePaymentStatus(String orderId, Boolean validPayment) {
    log("6.1. Update payment status: orderId={}, validPayment={}", orderId, validPayment);
    return Mono.justOrEmpty(
            ORDERS.parallelStream().filter(order -> orderId.equals(order.getId())).findFirst())
        .map(
            order -> {
              order.setValidPayment(validPayment);
              return order;
            })
        .map(ReactorTest1::getOrder)
        .doOnNext(order -> log("6.2. Updating payment status: order={}", order));
  }

  private static OrderEntity getOrder(OrderModel order) {
    return MODEL_MAPPER
        .map(order, OrderEntity.class)
        .withOrderId(order.getId())
        .withTotalAmount(order.getTotal())
        .withRegistrationDate(order.getRegistrationDate().atZone(ZoneId.of("America/Sao_Paulo")));
  }

  // Core layer
  public Mono<OrderEntity> saveOrder(Long customerId) {
    return getCustomerById(customerId)
        .map(customer -> new OrderEntity().withCustomer(customer))
        .flatMap(
            order ->
                Mono.zip(
                    getCustomerCardByCustomerId(order.getCustomer().getCustomerId()),
                    getCartItemsByCustomerId(order.getCustomer().getCustomerId())
                        .parallel()
                        .ordered(ReactorTest1::compare)
                        .collectList()
                        .map(
                            items -> {
                              if (CollectionUtils.isEmpty(items))
                                throw new RuntimeException("There aren't items in the cart");
                              return items;
                            }),
                    (card, items) -> order.withCard(card).withItems(items)))
        .flatMap(
            order -> {
              order.setRegistrationDate(ZonedDateTime.now());
              order.setTotalAmount(getTotalAmount(order.getItems()));
              order.setValidPayment(false);
              return saveOrder(order);
            })
        .flatMap(
            order -> {
              return isValidPaymentByCustomerId(order.getCustomer().getCustomerId())
                  .flatMap(
                      isValidPayment -> updatePaymentStatus(order.getOrderId(), isValidPayment));
            });
  }

  private static double getTotalAmount(List<CartItem> items) {
    return items.parallelStream().mapToDouble(CartItem::getTotalAmount).sum();
  }

  private static int compare(CartItem item1, CartItem item2) {
    if (item2.getTotalAmount() > item1.getTotalAmount()) return 1;
    if (item2.getTotalAmount() < item1.getTotalAmount()) return -1;
    return 0;
  }

  private static void log(String message, Object... args) {
    if (SHOW_LOGS) log.info(message, args);
  }

  // Webclient layer
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CustomerResponse {
    private Long id;
    private String firstName;
    private String lastName;
  }

  // Repository layer
  @Data
  @NoArgsConstructor
  public static class CustomerModel {
    private Long customerId;
    private String fullName;
  }

  // Core layer
  @Data
  @With
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Customer {
    private Long customerId;
    private String fullName;
  }

  // Webclient layer
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CustomerCardResponse {
    private Long customerId;
    private String longNum;
  }

  // Repository layer
  @Data
  @NoArgsConstructor
  public static class CustomerCardModel {
    private String longNum;
  }

  // Core layer
  @Data
  @With
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CustomerCard {
    private String longNum;
  }

  // Webclient layer
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CartItemResponse {
    private Long customerId;
    private Long productId;
    private Integer quantity;
    private Double unitPrice;
  }

  // Repository layer
  @Data
  @NoArgsConstructor
  public static class CartItemModel {
    private Long productId;
    private Double totalAmount;
  }

  // Core layer
  @Data
  @With
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CartItem {
    private Long productId;
    private Double totalAmount;
  }

  // Repository layer
  @Data
  @NoArgsConstructor
  public static class OrderModel {
    private String id;
    private CustomerModel customer;
    private CustomerCardModel card;
    private List<CartItemModel> items;
    private LocalDateTime registrationDate;
    private Double total;
    private Boolean validPayment;
  }

  // Core layer
  @Data
  @With
  @NoArgsConstructor
  @AllArgsConstructor
  public static class OrderEntity {
    private String orderId;
    private Customer customer;
    private CustomerCard card;
    private List<CartItem> items;
    private ZonedDateTime registrationDate;
    private Double totalAmount;
    private Boolean validPayment;
  }
}