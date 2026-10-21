
# ChatTurnView


## Properties

Name | Type
------------ | -------------
`turnId` | string
`seq` | number
`status` | string
`user` | [UserMessageView](UserMessageView.md)
`thinking` | [ThinkingView](ThinkingView.md)
`tools` | [Array&lt;ToolCardView&gt;](ToolCardView.md)
`assistant` | [AssistantMessageView](AssistantMessageView.md)
`usage` | [TokenUsageDto](TokenUsageDto.md)
`primedMemories` | number

## Example

```typescript
import type { ChatTurnView } from ''

// TODO: Update the object below with actual values
const example = {
  "turnId": null,
  "seq": null,
  "status": null,
  "user": null,
  "thinking": null,
  "tools": null,
  "assistant": null,
  "usage": null,
  "primedMemories": null,
} satisfies ChatTurnView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ChatTurnView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


