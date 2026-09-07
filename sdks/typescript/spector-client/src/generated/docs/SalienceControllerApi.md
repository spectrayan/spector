# SalienceControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**getSalienceConfig**](SalienceControllerApi.md#getsalienceconfig) | **GET** /api/v1/config/salience |  |
| [**rescoreMemories**](SalienceControllerApi.md#rescorememories) | **PUT** /api/v1/config/salience/rescore |  |
| [**updateInterests**](SalienceControllerApi.md#updateinterests) | **PUT** /api/v1/config/salience/interests |  |
| [**updatePersona**](SalienceControllerApi.md#updatepersona) | **PUT** /api/v1/config/salience/persona |  |
| [**updateWeights**](SalienceControllerApi.md#updateweights) | **PUT** /api/v1/config/salience/weights |  |



## getSalienceConfig

> SalienceSnapshot getSalienceConfig()



### Example

```ts
import {
  Configuration,
  SalienceControllerApi,
} from '';
import type { GetSalienceConfigRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new SalienceControllerApi(config);

  try {
    const data = await api.getSalienceConfig();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**SalienceSnapshot**](SalienceSnapshot.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## rescoreMemories

> { [key: string]: any | null; } rescoreMemories()



### Example

```ts
import {
  Configuration,
  SalienceControllerApi,
} from '';
import type { RescoreMemoriesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new SalienceControllerApi(config);

  try {
    const data = await api.rescoreMemories();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

**{ [key: string]: any | null; }**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## updateInterests

> { [key: string]: any | null; } updateInterests(interestsRequest)



### Example

```ts
import {
  Configuration,
  SalienceControllerApi,
} from '';
import type { UpdateInterestsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new SalienceControllerApi(config);

  const body = {
    // InterestsRequest
    interestsRequest: ...,
  } satisfies UpdateInterestsRequest;

  try {
    const data = await api.updateInterests(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **interestsRequest** | [InterestsRequest](InterestsRequest.md) |  | |

### Return type

**{ [key: string]: any | null; }**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## updatePersona

> { [key: string]: any | null; } updatePersona(personaContext)



### Example

```ts
import {
  Configuration,
  SalienceControllerApi,
} from '';
import type { UpdatePersonaRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new SalienceControllerApi(config);

  const body = {
    // PersonaContext
    personaContext: ...,
  } satisfies UpdatePersonaRequest;

  try {
    const data = await api.updatePersona(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **personaContext** | [PersonaContext](PersonaContext.md) |  | |

### Return type

**{ [key: string]: any | null; }**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## updateWeights

> { [key: string]: any | null; } updateWeights(weightsRequest)



### Example

```ts
import {
  Configuration,
  SalienceControllerApi,
} from '';
import type { UpdateWeightsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new SalienceControllerApi(config);

  const body = {
    // WeightsRequest
    weightsRequest: ...,
  } satisfies UpdateWeightsRequest;

  try {
    const data = await api.updateWeights(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **weightsRequest** | [WeightsRequest](WeightsRequest.md) |  | |

### Return type

**{ [key: string]: any | null; }**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

