
# NamespaceResponse


## Properties

Name | Type
------------ | -------------
`namespaceId` | string
`slug` | string
`ownerAccountId` | string
`type` | string
`status` | string
`displayName` | string
`description` | string
`bias` | [NamespaceBias](NamespaceBias.md)
`createdAt` | Date
`lastAccessedAt` | Date
`legalHold` | boolean

## Example

```typescript
import type { NamespaceResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "namespaceId": null,
  "slug": null,
  "ownerAccountId": null,
  "type": null,
  "status": null,
  "displayName": null,
  "description": null,
  "bias": null,
  "createdAt": null,
  "lastAccessedAt": null,
  "legalHold": null,
} satisfies NamespaceResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as NamespaceResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


