
# ConfigureTenantKeyRequest


## Properties

Name | Type
------------ | -------------
`provider` | string
`kekKeyId` | string
`credentialName` | string
`region` | string
`endpoint` | string

## Example

```typescript
import type { ConfigureTenantKeyRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "provider": null,
  "kekKeyId": null,
  "credentialName": null,
  "region": null,
  "endpoint": null,
} satisfies ConfigureTenantKeyRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ConfigureTenantKeyRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


