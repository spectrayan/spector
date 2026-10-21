
# TenantKeyStatusResponse


## Properties

Name | Type
------------ | -------------
`tenantId` | string
`byokEnabled` | boolean
`provider` | string
`kekKeyId` | string
`credentialName` | string
`region` | string
`endpoint` | string
`keyVersion` | string
`status` | string
`healthStatus` | string
`activeNamespaceDekCount` | number
`lastVerifiedAt` | Date
`lastError` | string
`createdAt` | Date
`updatedAt` | Date
`supportedProviders` | Set&lt;string&gt;

## Example

```typescript
import type { TenantKeyStatusResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "tenantId": null,
  "byokEnabled": null,
  "provider": null,
  "kekKeyId": null,
  "credentialName": null,
  "region": null,
  "endpoint": null,
  "keyVersion": null,
  "status": null,
  "healthStatus": null,
  "activeNamespaceDekCount": null,
  "lastVerifiedAt": null,
  "lastError": null,
  "createdAt": null,
  "updatedAt": null,
  "supportedProviders": null,
} satisfies TenantKeyStatusResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TenantKeyStatusResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


