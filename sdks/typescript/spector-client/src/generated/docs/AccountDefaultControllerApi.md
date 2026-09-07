# AccountDefaultControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**introspect**](AccountDefaultControllerApi.md#introspect) | **GET** /api/v1/account/introspect |  |
| [**setDefaultNamespace**](AccountDefaultControllerApi.md#setdefaultnamespaceoperation) | **PUT** /api/v1/account/default-namespace |  |



## introspect

> AccountIntrospectResponse introspect()



### Example

```ts
import {
  Configuration,
  AccountDefaultControllerApi,
} from '';
import type { IntrospectRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AccountDefaultControllerApi(config);

  try {
    const data = await api.introspect();
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

[**AccountIntrospectResponse**](AccountIntrospectResponse.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## setDefaultNamespace

> { [key: string]: string; } setDefaultNamespace(setDefaultNamespaceRequest)



### Example

```ts
import {
  Configuration,
  AccountDefaultControllerApi,
} from '';
import type { SetDefaultNamespaceOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AccountDefaultControllerApi(config);

  const body = {
    // SetDefaultNamespaceRequest
    setDefaultNamespaceRequest: ...,
  } satisfies SetDefaultNamespaceOperationRequest;

  try {
    const data = await api.setDefaultNamespace(body);
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
| **setDefaultNamespaceRequest** | [SetDefaultNamespaceRequest](SetDefaultNamespaceRequest.md) |  | |

### Return type

**{ [key: string]: string; }**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

