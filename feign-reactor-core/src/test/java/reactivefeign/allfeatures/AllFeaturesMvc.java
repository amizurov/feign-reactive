/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactivefeign.allfeatures;

import org.reactivestreams.Publisher;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

public interface AllFeaturesMvc extends AllFeaturesApi {

	@Override
	@GetMapping(path = "/mirrorParameters/{paramInPath}")
	Mono<Map<String, String>> mirrorParameters(
			@PathVariable("paramInPath") long paramInPath,
			@RequestParam("paramInUrl") String paramInUrl,
			@RequestParam Map<String, String> paramMap);

	@Override
	@GetMapping(path = "/mirrorParametersNew")
	Mono<Map<String, String>> mirrorParametersNew(
			@RequestParam("paramInUrl") long paramInUrl,
			@RequestParam(value = "dynamicParam", required = false) Long dynamicParam,
			@RequestParam Map<String, String> paramMap);

	@Override
	@GetMapping(path = "/mirrorListParametersNew")
	Mono<List<Integer>> mirrorListParametersNew(
			@RequestParam(value = "dynamicListParam", required = false) List<Integer> listParams);

	@GetMapping(path = "/mirrorMapParametersNew")
	Mono<Map<String, List<String>>> mirrorMapParametersNew(
			//Spring can't treat Map<String, List<String>> correctly
			@RequestParam MultiValueMap<String, String> paramMap);

	@Override
	@GetMapping(path = "/mirrorHeaders")
	Mono<Map<String, String>> mirrorHeaders(
			@RequestHeader("Method-Header") long param,
			@RequestHeader Map<String, String> headersMap);

	@Override
	@GetMapping(path = "/mirrorListHeaders")
	Mono<List<Long>> mirrorListHeaders(
			@RequestHeader("Method-Header") List<Long> param);

	@GetMapping(path = "/mirrorMultiMapHeaders")
	Mono<MultiValueMap<String, String>> mirrorMultiMapHeaders(
			//Spring can't treat Map<String, List<String>> correctly
			@RequestHeader MultiValueMap<String, String> param);

	@Override
	@PostMapping(path = "/mirrorBody")
	Mono<String> mirrorBody(@RequestBody String body);

	@Override
	@PostMapping(path = "/mirrorBodyMap")
	Mono<Map<String, String>> mirrorBodyMap(
			@RequestBody Map<String, String> body);

	@Override
	@PostMapping(path = "/mirrorBodyReactive")
	Mono<String> mirrorBodyReactive(@RequestBody Publisher<String> body);

	@Override
	@PostMapping(path = "/mirrorBodyMapReactive")
	Mono<Map<String, String>> mirrorBodyMapReactive(
			@RequestBody Publisher<Map<String, String>> body);

	@Override
	@PostMapping(path = "/returnBodyStream")
	Flux<TestObject> returnBodyStream();

	@Override
	@PostMapping(path = "/mirrorBodyStream")
	Flux<TestObject> mirrorBodyStream(
			@RequestBody Publisher<TestObject> bodyStream);


	@Override
	@PostMapping(path = "/mirrorIntegerBodyStream")
	Flux<Integer> mirrorIntegerBodyStream(
			@RequestBody Flux<Integer> body);

	@Override
	@PostMapping(path = "/mirrorStringBodyStream")
	Flux<String> mirrorStringBodyStream(
			@RequestBody  Flux<String> body);

	@Override
	@PostMapping(path = "/mirrorBodyWithDelay")
	Mono<String> mirrorBodyWithDelay(@RequestBody String body);

	@Override
	@PostMapping(path = "/mirrorStreamingBinaryBodyReactive")
	Flux<ByteBuffer> mirrorStreamingBinaryBodyReactive(@RequestBody Publisher<ByteBuffer> body);

	@Override
	@GetMapping(path = "/urlNotSubstituted")
	Mono<String> urlNotSubstituted();

	@Override
	@GetMapping(path = "/empty")
	Mono<TestObject> empty();

	@Override
	@GetMapping(path = "/encode")
	Mono<TestObject> encodeParam(@RequestParam("id") String param);

	@Override
	@GetMapping(path = "/encode/{id}")
	Mono<TestObject> encodePath(@PathVariable("id") String param);


}
