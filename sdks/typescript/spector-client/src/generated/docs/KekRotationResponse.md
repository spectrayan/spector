
# KekRotationResponse


## Properties

Name | Type
------------ | -------------
`tenantId` | string
`provider` | string
`previousKekKeyId` | string
`previousKekVersion` | string
`newKekKeyId` | string
`newKekVersion` | string
`rewrappedDekCount` | number
`refreshedCachedDeks` | number
`dataFilesRewritten` | boolean
`rotatedAt` | Date

## Example

```typescript
import type { KekRotationResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "tenantId": null,
  "provider": null,
  "previousKekKeyId": null,
  "previousKekVersion": null,
  "newKekKeyId": null,
  "newKekVersion": null,
  "rewrappedDekCount": null,
  "refreshedCachedDeks": null,
  "dataFilesRewritten": null,
  "rotatedAt": null,
} satisfies KekRotationResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as KekRotationResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


