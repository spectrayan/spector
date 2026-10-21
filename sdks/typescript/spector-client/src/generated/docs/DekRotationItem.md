
# DekRotationItem


## Properties

Name | Type
------------ | -------------
`namespaceId` | string
`previousDekVersion` | number
`newDekVersion` | number
`partitionBundlesEncrypted` | number
`recordsReKeyed` | number
`walEventsEncrypted` | number
`runtimeBundleEncrypted` | boolean

## Example

```typescript
import type { DekRotationItem } from ''

// TODO: Update the object below with actual values
const example = {
  "namespaceId": null,
  "previousDekVersion": null,
  "newDekVersion": null,
  "partitionBundlesEncrypted": null,
  "recordsReKeyed": null,
  "walEventsEncrypted": null,
  "runtimeBundleEncrypted": null,
} satisfies DekRotationItem

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as DekRotationItem
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


