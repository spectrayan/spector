# CredentialControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createCredential**](CredentialControllerApi.md#createcredentialoperation) | **POST** /api/v1/credentials |  |
| [**deleteCredential**](CredentialControllerApi.md#deletecredential) | **DELETE** /api/v1/credentials/{name} |  |
| [**getCredential**](CredentialControllerApi.md#getcredential) | **GET** /api/v1/credentials/{name} |  |
| [**listCredentials**](CredentialControllerApi.md#listcredentials) | **GET** /api/v1/credentials |  |
| [**testCredential**](CredentialControllerApi.md#testcredential) | **POST** /api/v1/credentials/{name}/test |  |
| [**updateCredential**](CredentialControllerApi.md#updatecredentialoperation) | **PUT** /api/v1/credentials/{name} |  |



## createCredential

> CredentialResponse createCredential(createCredentialRequest, xTenantID)



### Example

```ts
import {
  Configuration,
  CredentialControllerApi,
} from '';
import type { CreateCredentialOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CredentialControllerApi(config);

  const body = {
    // CreateCredentialRequest
    createCredentialRequest: ...,
    // string (optional)
    xTenantID: xTenantID_example,
  } satisfies CreateCredentialOperationRequest;

  try {
    const data = await api.createCredential(body);
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
| **createCredentialRequest** | [CreateCredentialRequest](CreateCredentialRequest.md) |  | |
| **xTenantID** | `string` |  | [Optional] [Defaults to `&#39;default&#39;`] |

### Return type

[**CredentialResponse**](CredentialResponse.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Created |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## deleteCredential

> deleteCredential(name, xTenantID)



### Example

```ts
import {
  Configuration,
  CredentialControllerApi,
} from '';
import type { DeleteCredentialRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CredentialControllerApi(config);

  const body = {
    // string
    name: name_example,
    // string (optional)
    xTenantID: xTenantID_example,
  } satisfies DeleteCredentialRequest;

  try {
    const data = await api.deleteCredential(body);
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
| **name** | `string` |  | [Defaults to `undefined`] |
| **xTenantID** | `string` |  | [Optional] [Defaults to `&#39;default&#39;`] |

### Return type

`void` (Empty response body)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **204** | No Content |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getCredential

> CredentialResponse getCredential(name, xTenantID)



### Example

```ts
import {
  Configuration,
  CredentialControllerApi,
} from '';
import type { GetCredentialRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CredentialControllerApi(config);

  const body = {
    // string
    name: name_example,
    // string (optional)
    xTenantID: xTenantID_example,
  } satisfies GetCredentialRequest;

  try {
    const data = await api.getCredential(body);
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
| **name** | `string` |  | [Defaults to `undefined`] |
| **xTenantID** | `string` |  | [Optional] [Defaults to `&#39;default&#39;`] |

### Return type

[**CredentialResponse**](CredentialResponse.md)

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


## listCredentials

> Array&lt;CredentialResponse&gt; listCredentials(xTenantID, userId)



### Example

```ts
import {
  Configuration,
  CredentialControllerApi,
} from '';
import type { ListCredentialsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CredentialControllerApi(config);

  const body = {
    // string (optional)
    xTenantID: xTenantID_example,
    // string (optional)
    userId: userId_example,
  } satisfies ListCredentialsRequest;

  try {
    const data = await api.listCredentials(body);
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
| **xTenantID** | `string` |  | [Optional] [Defaults to `&#39;default&#39;`] |
| **userId** | `string` |  | [Optional] [Defaults to `undefined`] |

### Return type

[**Array&lt;CredentialResponse&gt;**](CredentialResponse.md)

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


## testCredential

> { [key: string]: any | null; } testCredential(name, xTenantID)



### Example

```ts
import {
  Configuration,
  CredentialControllerApi,
} from '';
import type { TestCredentialRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CredentialControllerApi(config);

  const body = {
    // string
    name: name_example,
    // string (optional)
    xTenantID: xTenantID_example,
  } satisfies TestCredentialRequest;

  try {
    const data = await api.testCredential(body);
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
| **name** | `string` |  | [Defaults to `undefined`] |
| **xTenantID** | `string` |  | [Optional] [Defaults to `&#39;default&#39;`] |

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


## updateCredential

> CredentialResponse updateCredential(name, updateCredentialRequest, xTenantID)



### Example

```ts
import {
  Configuration,
  CredentialControllerApi,
} from '';
import type { UpdateCredentialOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CredentialControllerApi(config);

  const body = {
    // string
    name: name_example,
    // UpdateCredentialRequest
    updateCredentialRequest: ...,
    // string (optional)
    xTenantID: xTenantID_example,
  } satisfies UpdateCredentialOperationRequest;

  try {
    const data = await api.updateCredential(body);
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
| **name** | `string` |  | [Defaults to `undefined`] |
| **updateCredentialRequest** | [UpdateCredentialRequest](UpdateCredentialRequest.md) |  | |
| **xTenantID** | `string` |  | [Optional] [Defaults to `&#39;default&#39;`] |

### Return type

[**CredentialResponse**](CredentialResponse.md)

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

