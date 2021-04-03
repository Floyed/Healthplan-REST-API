package edu.neu.info7255.healthplan;

import edu.neu.info7255.healthplan.controller.PlanController;
import net.bytebuddy.implementation.bind.MethodDelegationBinder;
import org.junit.Before;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;


import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthplanApplicationTests {

	@LocalServerPort
	private int port;

	@Autowired
	private PlanController planController;

	private MockRestServiceServer mockServer;

	@Bean
	public RestTemplate getRestTemplate() {
		return new RestTemplate();
	}

	@Before
	public void init() {
		mockServer = MockRestServiceServer.createServer(this.getRestTemplate());
	}

	@Test
	void contextLoads() {



	}


	@Test
	public void greetingShouldReturnDefaultMessage() throws Exception {

//		try{
//
//			HttpHeaders headers = new HttpHeaders();
//			headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
//			HttpEntity <String> entity = new HttpEntity<String>(headers);
//
//			String response = getRestTemplate().exchange("http://localhost:+" + port+"+/plan/123", HttpMethod.GET, null, String.class).getBody();
//
//			System.out.println(response);
//
//		} catch(Exception e){
//			e.printStackTrace();
//		}
	}

}
