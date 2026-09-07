
# UserSummary


## Properties

Name | Type
------------ | -------------
`userId` | string
`username` | string
`email` | string
`displayName` | string
`roles` | Set&lt;string&gt;
`scopes` | Set&lt;string&gt;
`mustChangePassword` | boolean
`active` | boolean
`failedLoginCount` | number
`lockedUntil` | Date
`lastLoginAt` | Date
`createdAt` | Date
`updatedAt` | Date

## Example

```typescript
import type { UserSummary } from ''

// TODO: Update the object below with actual values
const example = {
  "userId": null,
  "username": null,
  "email": null,
  "displayName": null,
  "roles": null,
  "scopes": null,
  "mustChangePassword": null,
  "active": null,
  "failedLoginCount": null,
  "lockedUntil": null,
  "lastLoginAt": null,
  "createdAt": null,
  "updatedAt": null,
} satisfies UserSummary

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as UserSummary
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


