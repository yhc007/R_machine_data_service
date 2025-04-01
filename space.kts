job("Build and push Docker") {
  container(displayName = "docker stage", image = "unomic.registry.jetbrains.space/p/elfin-ap/containers/sbt:1.6.2") {
    shellScript {
      content = """
                    sbt "Docker / stage"
                    cp -r target/docker/stage $mountDir/share
                """
    }
  }

  docker {
    beforeBuildScript {
      content = "cp -r $mountDir/share/stage docker"
    }

    build {
      context = "docker"
    }

    push("unomic.registry.jetbrains.space/p/elfin-ap/containers/sirjin-data-service") {
      tags("latest")
    }
  }

  container(displayName = "Notify", image = "gradle") {
    kotlinScript { api ->
      val spaceUrl = api.spaceUrl()
      val executionNumber = api.executionNumber()
      val projectKey = api.projectKey()
      val repositoryName = "sirjin-data-service"
      val channel = ChatChannel.FromName("Automation Service")
      val packageUrl = "${spaceUrl}/p/${projectKey}/packages/container/containers/${repositoryName}"

      api.space().chats.messages.sendMessage(
        content = ChatMessage.Block(
          style = MessageStyle.SUCCESS,
          outline = null,
          sections = listOf(
            MessageSectionV2(
              elements = listOf(
                MessageText(
                  accessory = null,
                  style = null,
                  size = MessageTextSize.LARGE,
                  content = "[${repositoryName}](${packageUrl}) #${executionNumber}"
                ),
                MessageText(
                  accessory = null,
                  style = null,
                  size = null,
                  content = "새 버전의 컨테이너 이미지가 업데이트 되었습니다.",
                )
              ),
              style = null,
              textSize = null,
            )
          ),
          messageData = null,
        ),
        channel = ChannelIdentifier.Channel(channel)
      )
    }
  }
}
