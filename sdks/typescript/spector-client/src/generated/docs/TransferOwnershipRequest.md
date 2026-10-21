
# TransferOwnershipRequest


## Properties

Name | Type
------------ | -------------
`newOwnerAccountId` | string
`previousOwnerGrantRole` | string
`reason` | string

## Example

```typescript
import type { TransferOwnershipRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "newOwnerAccountId": null,
  "previousOwnerGrantRole": null,
  "reason": null,
} satisfies TransferOwnershipRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TransferOwnershipRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


