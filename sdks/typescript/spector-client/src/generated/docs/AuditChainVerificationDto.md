
# AuditChainVerificationDto


## Properties

Name | Type
------------ | -------------
`valid` | boolean
`checkedCount` | number
`lastEntryHash` | string
`firstInvalidEventId` | string
`message` | string
`verifiedAt` | Date

## Example

```typescript
import type { AuditChainVerificationDto } from ''

// TODO: Update the object below with actual values
const example = {
  "valid": null,
  "checkedCount": null,
  "lastEntryHash": null,
  "firstInvalidEventId": null,
  "message": null,
  "verifiedAt": null,
} satisfies AuditChainVerificationDto

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AuditChainVerificationDto
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


