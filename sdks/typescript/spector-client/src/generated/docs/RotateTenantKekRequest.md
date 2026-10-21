
# RotateTenantKekRequest


## Properties

Name | Type
------------ | -------------
`newKekKeyId` | string
`newKekVersion` | string
`provider` | string
`credentialName` | string
`region` | string
`endpoint` | string

## Example

```typescript
import type { RotateTenantKekRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "newKekKeyId": null,
  "newKekVersion": null,
  "provider": null,
  "credentialName": null,
  "region": null,
  "endpoint": null,
} satisfies RotateTenantKekRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RotateTenantKekRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


