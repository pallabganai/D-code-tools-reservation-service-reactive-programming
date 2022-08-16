package com.example.reservationservice;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
//for Mongo import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class ReservationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReservationServiceApplication.class, args);
	}

}

@Component
@RequiredArgsConstructor
@Log4j2
class SampleDataInitialiser {
	private final ReservationRepository reservationRepository;

	@EventListener(ApplicationReadyEvent.class)
	public void go() {
		Flux<Reservation> names = Flux
				.just("My", "Name", "Is", "Pallab", "Kumar", "Ganai")
				.map(s -> new Reservation(null, s))
				.flatMap(this.reservationRepository::save);

		this.reservationRepository
				.deleteAll()
				.thenMany(names)
				.thenMany(this.reservationRepository.findAll())
				.subscribe(log::info);
	}
}

//for Mongo interface ReservationRepository extends ReactiveCrudRepository<Reservation, String> {}
interface ReservationRepository extends ReactiveCrudRepository<Reservation, Integer> {}

// for mongo @Document
@Data
@AllArgsConstructor
@NoArgsConstructor
class Reservation {
	@Id
	private Integer id;
	// for mongo private String id;

	private String name;
}

//Not required for mongo
@Configuration
@Log4j2
class R2DBCConfiguration {
	@Bean
	ConnectionFactory connectionFactory(@Value("${spring.r2dbc.url}") String url) {
		log.info("DB url is {}", url);
		return ConnectionFactories.get(url);
	}
}

@Configuration
class HttpConfiguration {
	@Bean
	RouterFunction<ServerResponse> routes(ReservationRepository reservationRepository) {
		return route()
			.GET("/reservations",
					serverRequest -> ServerResponse.ok().body(reservationRepository.findAll(), Reservation.class))
				.build();
	}
}

@Configuration
class WebsocketConfig {
	@Bean
	SimpleUrlHandlerMapping simpleUrlHandlerMapping(WebSocketHandler wsh) {
		Map<String, WebSocketHandler> map = new HashMap<>();
		map.put("/ws/greetings", wsh);
		SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
		mapping.setOrder(10);
		mapping.setUrlMap(map);
		return mapping;
	}

	@Bean
	WebSocketHandlerAdapter webSocketHandlerAdapter() {
		return new WebSocketHandlerAdapter();
	}

	@Bean
	WebSocketHandler webSocketHandler(GreetingsProducer greetingsProducer) {
		return new WebSocketHandler() {
			@Override
			public Mono<Void> handle(WebSocketSession session) {
				Flux<WebSocketMessage> response = session
						.receive()
						.map(WebSocketMessage::getPayloadAsText)
						.map(GreetingRequest::new)
						.flatMap(greetingsProducer::greetingRequestFlux)
						.map(GreetingResponse::getMessage)
						.map(s -> session.textMessage(s));

				return session.send(response);
			}
		};
	}
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class GreetingRequest {
	private String name;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class GreetingResponse {
	private String message;
}

@Component
class GreetingsProducer {
	Flux<GreetingResponse> greetingRequestFlux(GreetingRequest greetingRequest) {
		return Flux
			.fromStream(Stream.generate(() -> new GreetingResponse("hello " +greetingRequest.getName() + " @ " + Instant.now() + "!")))
			.delayElements(Duration.ofSeconds(1));
	}
}