# UserSalienceControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**deleteProfile**](UserSalienceControllerApi.md#deleteprofile) | **DELETE** /api/v1/salience/{scope}/{id} |  |
| [**getProfile**](UserSalienceControllerApi.md#getprofile) | **GET** /api/v1/salience/{scope}/{id} |  |
| [**rescoreMemories1**](UserSalienceControllerApi.md#rescorememories1) | **POST** /api/v1/salience/rescore |  |
| [**rescoreStatus**](UserSalienceControllerApi.md#rescorestatus) | **GET** /api/v1/salience/rescore/status |  |
| [**saveProfile**](UserSalienceControllerApi.md#saveprofileoperation) | **PUT** /api/v1/salience/{scope}/{id} |  |



## deleteProfile

> { [key: string]: any | null; } deleteProfile(scope, id)



### Example

```ts
import {
  Configuration,
  UserSalienceControllerApi,
} from '';
import type { DeleteProfileRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new UserSalienceControllerApi(config);

  const body = {
    // string
    scope: scope_example,
    // string
    id: id_example,
  } satisfies DeleteProfileRequest;

  try {
    const data = await api.deleteProfile(body);
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
| **scope** | `string` |  | [Defaults to `undefined`] |
| **id** | `string` |  | [Defaults to `undefined`] |

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


## getProfile

> UserProfileDto getProfile(scope, id)



### Example

```ts
import {
  Configuration,
  UserSalienceControllerApi,
} from '';
import type { GetProfileRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new UserSalienceControllerApi(config);

  const body = {
    // string
    scope: scope_example,
    // string
    id: id_example,
  } satisfies GetProfileRequest;

  try {
    const data = await api.getProfile(body);
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
| **scope** | `string` |  | [Defaults to `undefined`] |
| **id** | `string` |  | [Defaults to `undefined`] |

### Return type

[**UserProfileDto**](UserProfileDto.md)

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


## rescoreMemories1

> { [key: string]: any | null; } rescoreMemories1(requestBody)



### Example

```ts
import {
  Configuration,
  UserSalienceControllerApi,
} from '';
import type { RescoreMemories1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new UserSalienceControllerApi(config);

  const body = {
    // { [key: string]: string; } (optional)
    requestBody: Object,
  } satisfies RescoreMemories1Request;

  try {
    const data = await api.rescoreMemories1(body);
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
| **requestBody** | `{ [key: string]: string; }` |  | [Optional] |

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


## rescoreStatus

> { [key: string]: any | null; } rescoreStatus()



### Example

```ts
import {
  Configuration,
  UserSalienceControllerApi,
} from '';
import type { RescoreStatusRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new UserSalienceControllerApi(config);

  try {
    const data = await api.rescoreStatus();
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


## saveProfile

> { [key: string]: any | null; } saveProfile(scope, id, saveProfileRequest)



### Example

```ts
import {
  Configuration,
  UserSalienceControllerApi,
} from '';
import type { SaveProfileOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new UserSalienceControllerApi(config);

  const body = {
    // string
    scope: scope_example,
    // string
    id: id_example,
    // SaveProfileRequest
    saveProfileRequest: ...,
  } satisfies SaveProfileOperationRequest;

  try {
    const data = await api.saveProfile(body);
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
| **scope** | `string` |  | [Defaults to `undefined`] |
| **id** | `string` |  | [Defaults to `undefined`] |
| **saveProfileRequest** | [SaveProfileRequest](SaveProfileRequest.md) |  | |

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

